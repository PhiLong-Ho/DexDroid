"""Bounded Kotlin lexical inventory; compilation remains the syntax/ABI authority."""

import re


def mask_literals(source):
    """Blank comments/strings without moving offsets, including nested templates."""
    size = len(source)

    def comment(start):
        if source.startswith("//", start):
            end = source.find("\n", start)
            return size if end < 0 else end
        depth, cursor = 1, start + 2
        while cursor < size:
            if source.startswith("/*", cursor):
                depth += 1
                cursor += 2
            elif source.startswith("*/", cursor):
                depth -= 1
                cursor += 2
                if depth == 0:
                    return cursor
            else:
                cursor += 1
        raise ValueError("Unclosed Kotlin comment")

    def interpolation(cursor, nesting):
        depth = 1
        while cursor < size:
            if source.startswith(("//", "/*"), cursor):
                cursor = comment(cursor)
            elif source[cursor] in "\"'":
                cursor = literal(cursor, nesting + 1)
            elif source[cursor] == "{":
                depth += 1
                cursor += 1
            elif source[cursor] == "}":
                depth -= 1
                cursor += 1
                if depth == 0:
                    return cursor
            else:
                cursor += 1
        raise ValueError("Unclosed Kotlin string template")

    def literal(start, nesting=0):
        if nesting > 64:
            raise ValueError("Kotlin string template nesting exceeds inventory limit")
        delimiter = '"""' if source.startswith('"""', start) else source[start]
        cursor = start + len(delimiter)
        while cursor < size:
            if source.startswith(delimiter, cursor):
                return cursor + len(delimiter)
            if delimiter != '"""' and source[cursor] == "\\":
                cursor += 2
            elif delimiter != "'" and source.startswith("${", cursor):
                cursor = interpolation(cursor + 2, nesting)
            else:
                cursor += 1
        raise ValueError("Unclosed Kotlin literal")

    result, cursor = list(source), 0
    while cursor < size:
        end = None
        if source.startswith(("//", "/*"), cursor):
            end = comment(cursor)
        elif source[cursor] in "\"'":
            end = literal(cursor)
        if end is None:
            cursor += 1
        else:
            for offset in range(cursor, end):
                if result[offset] not in "\r\n":
                    result[offset] = " "
            cursor = end
    return "".join(result)


def development_identities(path):
    """Inventory top-level declarations and JVM facades; nested names share roots."""
    source = path.read_text(encoding="utf-8")
    masked = mask_literals(source)
    package = re.findall(r"\bpackage\s+([A-Za-z_$]\w*(?:\.[A-Za-z_$]\w*)*)", masked)
    if len(package) > 1 or (re.search(r"\bpackage\b", masked) and not package):
        raise ValueError(f"Unsupported or ambiguous Kotlin package: {path}")
    if package and re.search(r"\bpackage\s+[^\r\n;]*`", masked):
        raise ValueError(f"Escaped Kotlin package requires explicit inventory support: {path}")
    prefix = package[0] + "." if package else ""
    if not re.fullmatch(r"[A-Za-z_$]\w*", path.stem):
        raise ValueError(f"Unsupported Kotlin facade filename: {path}")
    default_facade = path.stem[0].upper() + path.stem[1:] + "Kt"
    names = set()
    if re.search(r"@file\s*:\s*\[", masked) or re.search(
        r"\bimport\s+kotlin\.jvm\.(?:JvmName|JvmMultifileClass)\s+as\b", masked
    ):
        raise ValueError(f"Unsupported Kotlin file annotation syntax: {path}")
    annotation_prefix = r"@file\s*:\s*(?:kotlin\s*\.\s*jvm\s*\.\s*)?"
    annotations = list(re.finditer(annotation_prefix + r"JvmName\s*\(", masked))
    if len(annotations) > 1:
        raise ValueError(f"Ambiguous Kotlin JVM facade: {path}")
    facade = default_facade
    if annotations:
        argument = re.match(r'\s*"([A-Za-z_$]\w*)"\s*\)', source[annotations[0].end() :])
        if not argument:
            raise ValueError(f"Nonliteral Kotlin JVM facade: {path}")
        facade = argument[1]
    names.add(prefix + facade)
    if re.search(annotation_prefix + r"JvmMultifileClass\b", masked):
        if not annotations:
            raise ValueError(f"Kotlin multifile facade requires explicit JVM name: {path}")
        names.add(prefix + facade + "__" + default_facade)
    tokens = re.findall(r"::|[A-Za-z_$]\w*|\S", masked)
    depth = 0
    for index, token in enumerate(tokens):
        if token == "{":
            depth += 1
        elif token == "}":
            depth -= 1
            if depth < 0:
                raise ValueError(f"Unbalanced Kotlin source: {path}")
        elif depth == 0 and token in {"class", "interface", "object"}:
            if index and tokens[index - 1] == "::":
                continue
            name = tokens[index + 1] if index + 1 < len(tokens) else ""
            if re.fullmatch(r"[A-Za-z_$]\w*", name):
                names.add(prefix + name)
            elif token != "object" or name != ":":
                raise ValueError(f"Unsupported Kotlin declaration: {path}")
    if depth != 0:
        raise ValueError(f"Unbalanced Kotlin source: {path}")
    return names
