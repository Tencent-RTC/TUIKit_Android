package io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.pinyinhelper;
import java.util.Set;

public interface PinyinDict {
    Set<String> words();

    String[] toPinyin(String word);
}
