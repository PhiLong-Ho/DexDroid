"""Read literal Android version metadata without evaluating arbitrary Gradle.

Declarations must be direct, standalone statements in android/defaultConfig.
Unsupported template nesting fails closed. This is a bounded lexical contract.
"""

import re
from dataclasses import dataclass


@dataclass(frozen=True)
class Token:
    value: str
    kind: str
    start: int
    end: int


def _tokens(source):
    tokens, pos = [], 0
    while pos < len(source):
        if source[pos].isspace():
            pos += 1
            continue
        start = pos
        if source.startswith("//", pos):
            end = source.find("\n", pos)
            pos = len(source) if end < 0 else end
            continue
        if source.startswith("/*", pos):
            depth, pos = 1, pos + 2
            while depth and pos < len(source):
                if source.startswith("/*", pos):
                    depth, pos = depth + 1, pos + 2
                elif source.startswith("*/", pos):
                    depth, pos = depth - 1, pos + 2
                else:
                    pos += 1
            if depth:
                raise ValueError("Unclosed Gradle comment")
            continue
        if source[pos] in "\"'":
            quote = source[pos]
            if source.startswith(quote * 3, pos):
                end = source.find(quote * 3, pos + 3)
                if end < 0:
                    raise ValueError("Unclosed Gradle raw string")
                pos = end + 3
                kind = "raw"
            else:
                pos += 1
                while pos < len(source) and source[pos] != quote:
                    if source[pos] in "\r\n":
                        raise ValueError("Unclosed Gradle string")
                    pos += 2 if source[pos] == "\\" else 1
                if pos >= len(source):
                    raise ValueError("Unclosed Gradle string")
                pos += 1
                kind = "string"
            value = source[start:pos]
            if "${" in value:
                raise ValueError("Gradle template expressions are outside the literal contract")
            tokens.append(Token(value, kind, start, pos))
            continue
        match = re.match(r"[A-Za-z_][A-Za-z_0-9]*|[0-9]+", source[pos:])
        if match:
            pos += len(match[0])
            tokens.append(Token(match[0], "word", start, pos))
        else:
            pos += 1
            tokens.append(Token(source[start:pos], "punctuation", start, pos))
    return tokens


def _owners(tokens):
    stack, owners = [], []
    close = {"}": "{", ")": "(", "]": "["}
    for index, token in enumerate(tokens):
        owners.append(tuple(stack))
        if token.kind == "punctuation" and token.value in ("{", "(", "["):
            stack.append(index)
        elif token.kind == "punctuation" and token.value in close:
            if not stack or tokens[stack[-1]].value != close[token.value]:
                raise ValueError("Unbalanced Gradle structure")
            stack.pop()
    if stack:
        raise ValueError("Unclosed Gradle structure")
    return owners


def app_version(root):
    paths = [root / "app/build.gradle.kts", root / "app/build.gradle"]
    paths = [path for path in paths if path.is_file()]
    if len(paths) != 1:
        raise ValueError("Expected exactly one app/build.gradle.kts or app/build.gradle")
    path = paths[0]
    kotlin = path.suffix == ".kts"
    source = path.read_text(encoding="utf-8")
    tokens = _tokens(source)
    owners = _owners(tokens)

    def line_start(index):
        start = source.rfind("\n", 0, tokens[index].start) + 1
        return not any(start <= token.start < tokens[index].start for token in tokens)

    def scalar(token):
        if token.kind == "string":
            quote = '"' if kotlin else "['\"]"
            return (
                bool(re.fullmatch(quote + r"[A-Za-z0-9][A-Za-z0-9._+-]*" + quote, token.value))
                and token.value[0] == token.value[-1]
            )
        return token.kind == "word" and bool(re.fullmatch(r"[0-9]+", token.value))

    def independent(index, parent):
        if not line_start(index):
            return False
        if index == 0 or index - 1 in parent:
            return True
        previous = index - 1
        if "\n" not in source[tokens[previous].end : tokens[index].start]:
            return False
        if tokens[previous].value == "}" and owners[previous][:-1] == parent:
            return True
        # Admit a preceding complete direct scalar property, never a control
        # condition, expression-body function, infix call or receiver qualifier.
        key = previous - (2 if kotlin else 1)
        return (
            key >= 0
            and owners[key] == parent
            and tokens[key].kind == "word"
            and line_start(key)
            and scalar(tokens[previous])
            and (not kotlin or tokens[key + 1].value == "=")
        )

    def block(name, parent):
        found = [
            i for i, token in enumerate(tokens) if token.kind == "word" and token.value == name
        ]
        if len(found) != 1:
            raise ValueError(f"Expected one unambiguous {name} block")
        index = found[0]
        if (
            owners[index] != parent
            or index + 1 >= len(tokens)
            or tokens[index + 1].value != "{"
            or not independent(index, parent)
        ):
            raise ValueError(f"Expected direct {name} block")
        return index + 1

    android = block("android", ())
    config = block("defaultConfig", (android,))
    forbidden = {"setVersionName", "setVersionCode", "versionNameSuffix"}
    if any(token.kind == "word" and token.value in forbidden for token in tokens):
        raise ValueError("Alternate version mutation is outside the literal contract")
    fields = {
        "applicationId": "string",
        "minSdk": "number",
        "targetSdk": "number",
        "versionCode": "number",
        "versionName": "string",
        "testInstrumentationRunner": "string",
    }
    values = {}
    index = config + 1
    while index < len(tokens) and tokens[index].value != "}":
        key = tokens[index].value
        if (
            tokens[index].kind != "word"
            or key not in fields
            or key in values
            or owners[index] != (android, config)
            or not line_start(index)
        ):
            raise ValueError("Unsupported or ambiguous defaultConfig statement")
        literal_index = index + (2 if kotlin else 1)
        if literal_index >= len(tokens) or (kotlin and tokens[index + 1].value != "="):
            raise ValueError(f"Expected literal assignment for {key}")
        token = tokens[literal_index]
        if fields[key] == "string":
            quote = '"' if kotlin else "['\"]"
            pattern = quote + r"([A-Za-z0-9][A-Za-z0-9._+-]*)" + quote
            match = re.fullmatch(pattern, token.value)
            if token.kind != "string" or not match or token.value[0] != token.value[-1]:
                raise ValueError(f"Expected safe literal {key}")
            values[key] = match[1]
        else:
            if not re.fullmatch(r"[1-9][0-9]*", token.value):
                raise ValueError(f"Expected positive literal {key}")
            values[key] = int(token.value)

        if literal_index + 1 < len(tokens):
            following = tokens[literal_index + 1]
            if "\n" not in source[token.end : following.start]:
                raise ValueError(f"Expected complete literal {key} statement")
        index = literal_index + 1
    for key in ("versionName", "versionCode"):
        found = [i for i, token in enumerate(tokens) if token.kind == "word" and token.value == key]
        if key not in values or len(found) != 1 or owners[found[0]] != (android, config):
            raise ValueError(f"Expected one direct literal {key} in defaultConfig")
    return values["versionName"], values["versionCode"]
