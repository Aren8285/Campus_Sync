package com.campussync.app.core.navigation

object Routes {
    const val Welcome = "welcome"
    const val SignUp = "signup"
    const val Login = "login"
    const val ForgotPassword = "forgot_password"

    const val ProfileEdit = "profile/edit"
    const val PublicProfile = "profile/public/{uid}"

    const val CreatePost = "compose/post"
    const val PostDetail = "post/{postId}"

    const val CreateStory = "compose/story"
    const val StoryViewer = "story/{uid}"

    const val Chats = "chats"
    const val NewChat = "chats/new"
    const val ChatThread = "chat/{chatId}"

    const val Boards = "boards"
    const val BoardThreads = "boards/{boardId}"
    const val CreateThread = "boards/{boardId}/new"
    const val ThreadDetail = "boards/{boardId}/threads/{threadId}"

    const val PublicProfileArg = "uid"
    const val PostIdArg = "postId"
    const val StoryUidArg = "uid"
    const val ChatThreadArg = "chatId"
    const val BoardIdArg = "boardId"
    const val ThreadIdArg = "threadId"

    fun publicProfile(uid: String): String = "profile/public/$uid"

    fun postDetail(postId: String): String = "post/$postId"

    fun storyViewer(uid: String): String = "story/$uid"

    fun chatThread(chatId: String): String = "chat/$chatId"

    fun boardThreads(boardId: String): String = "boards/$boardId"

    fun createThread(boardId: String): String = "boards/$boardId/new"

    fun threadDetail(boardId: String, threadId: String): String =
        "boards/$boardId/threads/$threadId"
}
