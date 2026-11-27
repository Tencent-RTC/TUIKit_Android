package io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.pinyinhelper;
import org.ahocorasick.trie.Emit;

import java.util.Collection;
import java.util.List;

interface SegmentationSelector {
    List<Emit> select(Collection<Emit> emits);
}
