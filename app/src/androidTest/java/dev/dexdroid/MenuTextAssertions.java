package dev.dexdroid;

import android.view.*;
import android.widget.TextView;

/** Checks decoded, displayed text rather than source strings or click behavior. */
final class MenuTextAssertions {
 private static final String[] BROKEN={"\ufffd","\u00c2\u00b0","\u00c2\u00b7","\u00c3\u2014","\u00e2\u20ac","\u00c3\u00a2","\u00ef\u00bf\u00bd"};
 private static final String SYMBOLS="\u00b0\u00b7\u00d7\u2026\u203a\u2022\u2014\u21e7\u232b";
 private static void clean(CharSequence value){if(value==null)return;String text=value.toString();for(String broken:BROKEN)if(text.contains(broken))throw new AssertionError("Corrupted displayed text: "+text);}
 static int check(View view){int count=0;clean(view.getContentDescription());if(view instanceof TextView){TextView text=(TextView)view;clean(text.getText());clean(text.getHint());String value=text.getText().toString()+String.valueOf(text.getHint());for(int i=0;i<SYMBOLS.length();i++){String glyph=SYMBOLS.substring(i,i+1);if(value.contains(glyph)&&!text.getPaint().hasGlyph(glyph))throw new AssertionError("Missing font glyph U+"+Integer.toHexString(glyph.charAt(0)));}count++;}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=check(group.getChildAt(i));}return count;}
 static boolean contains(View view,String exact){if(view instanceof TextView&&((TextView)view).getText().toString().contains(exact))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),exact))return true;}return false;}
}
