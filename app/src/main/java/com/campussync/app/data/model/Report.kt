package com.campussync.app.data.model

/** What a report is about. */
enum class ReportTarget(val wireValue: String) {
    Post("post"),
    Comment("comment"),
    Story("story"),
    User("user"),
    Thread("thread"),
    BoardComment("board_comment")
}

/** The reasons offered in the report dialog. */
enum class ReportReason(val wireValue: String, val label: String) {
    Spam("spam", "Spam or promotion"),
    Harassment("harassment", "Harassment or bullying"),
    HateSpeech("hate_speech", "Hate speech"),
    Misinformation("misinformation", "Misinformation"),
    Other("other", "Something else")
}
