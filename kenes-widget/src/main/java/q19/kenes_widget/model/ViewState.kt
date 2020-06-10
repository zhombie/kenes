package q19.kenes_widget.model

internal sealed class ViewState {
    sealed class ChatBot : ViewState() {
        object Category : ChatBot()
        object UserPrompt : ChatBot()
    }

    sealed class TextDialog : ViewState() {
        object IDLE : TextDialog()
        object Pending : TextDialog()
        object Live : TextDialog()
        object UserDisconnected : TextDialog()
        object CallAgentDisconnected : TextDialog()
        class UserFeedback(val isFeedbackSent: Boolean) : TextDialog()
    }

    sealed class AudioDialog : ViewState() {
        object IDLE : AudioDialog()
        object Pending : AudioDialog()

        object Start : AudioDialog()
        object Preparation : AudioDialog()
        object Ready : AudioDialog()
        class Live(val isDialogScreenShown: Boolean = true) : AudioDialog()
        object UserDisconnected : AudioDialog()
        object CallAgentDisconnected : AudioDialog()

        class UserFeedback(val isFeedbackSent: Boolean) : AudioDialog()
    }

    sealed class VideoDialog : ViewState() {
        object IDLE : VideoDialog()
        object Pending : VideoDialog()

        object Start : VideoDialog()
        object Preparation : VideoDialog()
        object Ready : VideoDialog()
        class Live(val isDialogScreenShown: Boolean = true) : VideoDialog()
        object UserDisconnected : VideoDialog()
        object CallAgentDisconnected : VideoDialog()

        class UserFeedback(val isFeedbackSent: Boolean) : VideoDialog()
    }

    object Form : ViewState()

    object Info : ViewState()

}