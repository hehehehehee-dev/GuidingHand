package vn.guidinghand

enum class ConversationControl { STOP, PAUSE, CONTINUE, REPEAT, REPLAN, NEW_REQUEST;
    companion object {
        fun from(text: String): ConversationControl {
            val normalized = folded(text).trim(' ', '.', '!', '?')
            if (normalized.length < 100 && !Regex("\\b(nhung|but|muon|want)\\b").containsMatchIn(normalized)) {
                if (Regex("^(toi )?da mo .+|^i opened .+|^i am in .+").matches(normalized)) return CONTINUE
                if (Regex("^(khong thay|toi khong thay|khong co nut|that didn.t work|i can.t find|not there).*" ).matches(normalized)) return REPLAN
            }
            return when (normalized) {
            "stop", "cancel", "dung", "dung lai", "thoi", "huy" -> STOP
            "wait", "pause", "hold on", "doi", "doi chut", "cho chut", "tam dung" -> PAUSE
            "continue", "go on", "next", "i did that", "opened it", "tiep", "tiep di", "tiep tuc", "xong roi", "da mo roi" -> CONTINUE
            "repeat", "say that again", "where do i tap", "which button", "nhac lai", "noi lai", "chi lai", "bam dau", "bam nut nao" -> REPEAT
            else -> NEW_REQUEST
            }
        }
    }
}
