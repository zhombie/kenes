package q19.kenes_widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EdgeEffect
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.MergeAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fondesa.kpermissions.*
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.request.PermissionRequest
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.RequestParams
import com.nbsp.materialfilepicker.MaterialFilePicker
import com.nbsp.materialfilepicker.ui.FilePickerActivity
import com.squareup.picasso.Picasso
import org.webrtc.*
import q19.kenes_widget.adapter.ChatAdapter
import q19.kenes_widget.adapter.ChatAdapterItemDecoration
import q19.kenes_widget.adapter.ChatFooterAdapter
import q19.kenes_widget.core.locale.LocalizationActivity
import q19.kenes_widget.model.*
import q19.kenes_widget.model.Message
import q19.kenes_widget.network.file.DownloadResult
import q19.kenes_widget.network.file.downloadFile
import q19.kenes_widget.network.file.uploadFile
import q19.kenes_widget.network.http.IceServersTask
import q19.kenes_widget.network.http.WidgetConfigsTask
import q19.kenes_widget.network.socket.SocketClient
import q19.kenes_widget.ui.components.*
import q19.kenes_widget.util.*
import q19.kenes_widget.util.FileUtil.getFileType
import q19.kenes_widget.util.FileUtil.openFile
import q19.kenes_widget.webrtc.AppRTCAudioManager
import q19.kenes_widget.webrtc.ProxyVideoSink
import q19.kenes_widget.webrtc.SimpleSdpObserver
import java.io.File

class KenesWidgetV2Activity : LocalizationActivity(), PermissionRequest.Listener {

    companion object {
        private const val TAG = "LOL"

        private const val KEY_HOSTNAME = "hostname"

        private const val VIDEO_RESOLUTION_WIDTH = 1024
        private const val VIDEO_RESOLUTION_HEIGHT = 768
        private const val FPS = 24

        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val VIDEO_TRACK_ID = "ARDAMSv0"

        private const val FILE_PICKER_REQUEST_CODE = 101

        @JvmStatic
        fun newIntent(context: Context, hostname: String): Intent {
            return Intent(context, KenesWidgetV2Activity::class.java)
                .putExtra(KEY_HOSTNAME, hostname)
        }
    }

    // -------------------------- Binding views -----------------------------------

    /**
     * Parent root view [rootView].
     */
    private val rootView by bind<FrameLayout>(R.id.rootView)

    /**
     * Header view [headerView] for opponent info display.
     */
    private val headerView by bind<HeaderView>(R.id.headerView)

    /**
     * Video call [videoCallView] screen view.
     */
    private val videoCallView by bind<VideoCallView>(R.id.videoCallView)

    /**
     * Audio call [audioCallView] screen view.
     */
    private val audioCallView by bind<AudioCallView>(R.id.audioCallView)

    /**
     * Info screen view [infoView] with extra information & contacts.
     */
    private val infoView by bind<InfoView>(R.id.infoView)

    /**
     * Footer view [footerView] for messenger.
     */
    private val footerView by bind<FooterView>(R.id.footerView)

    /**
     * View [recyclerView] for chat.
     */
    private val recyclerView by bind<RecyclerView>(R.id.recyclerView)

    /**
     * User feedback view [feedbackView] after dialog view.
     */
    private val feedbackView by bind<FeedbackView>(R.id.feedbackView)

    private val formView by bind<FormView>(R.id.formView)

//    private val dynamicFormView by bind<DynamicFormView>(R.id.dynamicFormView)

    private val progressView by bind<ProgressView>(R.id.progressView)

    /**
     * Bottom navigation view variables: [bottomNavigationView]
     */
    private val bottomNavigationView by bind<BottomNavigationView>(R.id.bottomNavigationView)

    /**
     * Video dialog view variables: [videoDialogView]
     */
    private val videoDialogView by bind<VideoDialogView>(R.id.videoDialogView)

    /**
     * Audio dialog view variables: [audioDialogView]
     */
    private val audioDialogView by bind<AudioDialogView>(R.id.audioDialogView)

    // ------------------------------------------------------------------------

    private val httpClient by lazy { AsyncHttpClient() }

    private var palette = intArrayOf()

    private var mergeAdapter: MergeAdapter? = null
    private var chatAdapter: ChatAdapter? = null
    private var chatFooterAdapter: ChatFooterAdapter? = null

    private var socketClient: SocketClient? = null
    private var peerConnection: PeerConnection? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var rootEglBase: EglBase? = null

    private var localAudioSource: AudioSource? = null
    private var localVideoSource: VideoSource? = null

    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localMediaStream: MediaStream? = null

    private var localVideoCapturer: VideoCapturer? = null

    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null

    private var audioManager: AppRTCAudioManager? = null

    private val permissionsRequest by lazy {
        permissionsBuilder(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH
        ).build()
    }

    private var configs = Configs()
    private var chatBot = ChatBot()
    private var dialog = Dialog()

    private var iceServers = listOf<WidgetIceServer>()

    private var viewState: ViewState = ViewState.ChatBot
        set(value) {
            field = value
            runOnUiThread {
                updateViewState(value)
            }
        }

    private var isUserPromptMode: Boolean = false
        set(value) {
            if (field == value) {
                return
            }

            field = value

            if (value) {
                runOnUiThread {
//                    chatAdapter?.clearMessages(false)
                    chatAdapter?.clearCategoryMessages()
                }

                chatBot.activeCategory = null
            }

            chatBot.isLocked = value
        }

    private var isLoading: Boolean = false
        set(value) {
            if (field == value) return
            if (dialog.isOnLive) return

            field = value

            runOnUiThread {
                if (value) {
                    recyclerView.visibility = View.GONE
                    progressView.show()
                } else {
                    progressView.hide()
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }

    private var chatRecyclerState: Parcelable? = null

    private val chatAdapterDataObserver: RecyclerView.AdapterDataObserver by lazy {
        object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                recyclerView.adapter?.let { adapter ->
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    private val chatFooterAdapterDataObserver: RecyclerView.AdapterDataObserver by lazy {
        object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)

                recyclerView.adapter?.let { adapter ->
                    recyclerView.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }
    }

    private var previousVolumeControlStream: Int = AudioManager.STREAM_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.kenes_activity_widget_v2)

        val hostname = intent.getStringExtra(KEY_HOSTNAME)

        if (hostname.isNullOrBlank()) {
            throwError()
        } else {
            UrlUtil.setHostname(hostname)
        }

        permissionsRequest.addListener(this)
        permissionsRequest.send()

        /**
         * [Picasso] configuration
         */
        if (Picasso.get() == null) {
            Picasso.setSingletonInstance(Picasso.Builder(this).build())
        }

        palette = resources.getIntArray(R.array.kenes_palette)

        // --------------------- Default screen setups ----------------------------

        /**
         * Default active navigation button of [bottomNavigationView]
         */
        bottomNavigationView.setHomeNavButtonActive()
        isLoading = true

        /**
         * Default states of views
         */
        dialog.clear()

        isUserPromptMode = false

        headerView.hideHangupButton()
        headerView.setOpponentInfo(Configs.Opponent(
            "Kenes",
            "Smart Bot",
            drawableRes = R.drawable.kenes_ic_robot
        ))

        feedbackView.setDefaultState()
        footerView.setDefaultState()
        videoCallView.setDefaultState()
        audioCallView.setDefaultState()

        viewState = ViewState.ChatBot

        chatBot.callback = object : ChatBot.Callback {
            override fun onBasicCategoriesLoaded(categories: List<Category>) {
                val messages = categories
                    .sortedBy { it.id }
                    .mapIndexed { index, category ->
                        if (palette.isNotEmpty()) {
                            category.color = palette[index % palette.size]
                        }
                        Message(type = Message.Type.CATEGORY, category = category)
                    }

                runOnUiThread {
                    chatFooterAdapter?.clear()
                    chatAdapter?.setNewMessages(messages)
                }

                isLoading = false
            }
        }

        // ------------------------------------------------------------------------


        /**
         * Configuration of home bottom navigation button action listeners (click/touch)
         */
        bottomNavigationView.callback = object : BottomNavigationView.Callback {
            override fun onHomeNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.ChatBot
                    }
                    return false
                }

                isUserPromptMode = false

                chatBot.clear()

                chatAdapter?.clear()
                chatFooterAdapter?.clear()

                socketClient?.requestCategories(0, currentLanguage)

                isLoading = true

                viewState = ViewState.ChatBot

                return true
            }

            override fun onVideoNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.VideoDialog(State.IDLE)
                    }
                    return false
                }

                isUserPromptMode = false

                chatBot.clear()

                chatAdapter?.clear()
                chatFooterAdapter?.clear()

                viewState = ViewState.VideoDialog(State.IDLE)

                return true
            }

            override fun onAudioNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.AudioDialog(State.IDLE)
                    }
                    return false
                }

                isUserPromptMode = false

                chatBot.clear()

                chatAdapter?.clear()
                chatFooterAdapter?.clear()

                viewState = ViewState.AudioDialog(State.IDLE)

                return true
            }

            override fun onInfoNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.Info
                    }
                    return false
                }

                isUserPromptMode = false

                chatBot.clear()

                chatAdapter?.clear()
                chatFooterAdapter?.clear()

                viewState = ViewState.Info

                return true
            }
        }

        /**
         * Configuration of other button action listeners (click/touch)
         */
        headerView.callback = object : HeaderView.Callback {
            override fun onHangupButtonClicked() {
                showHangupConfirmAlert { hangupLiveCall() }
            }
        }

        videoCallView.setOnCallClickListener {
            val isPermissionRequestSent = checkPermissions()
            if (isPermissionRequestSent) {
                return@setOnCallClickListener
            } else {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.VideoDialog(State.IDLE)
                    }
                    return@setOnCallClickListener
                }

                dialog.isInitiator = true

                viewState = ViewState.VideoDialog(State.PENDING)

                socketClient?.videoCallToCallAgent(currentLanguage)
            }
        }

        audioCallView.setOnCallClickListener {
            val isPermissionRequestSent = checkPermissions()
            if (isPermissionRequestSent) {
                return@setOnCallClickListener
            } else {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.AudioDialog(State.IDLE)
                    }
                    return@setOnCallClickListener
                }

                dialog.isInitiator = true

                viewState = ViewState.AudioDialog(State.PENDING)

                socketClient?.audioCallToCallAgent(currentLanguage)
            }
        }

        formView.callback = object : FormView.Callback {
            override fun onCancelClicked() {
                viewState = ViewState.ChatBot
            }

            override fun onSendClicked(name: String, email: String, phone: String) {
                socketClient?.sendFuzzyTaskConfirmation(name, email, phone)

                showFormSentSuccess {
                    formView.clearInputViews()
                    viewState = ViewState.ChatBot
                }
            }
        }

//        dynamicFormView.callback = object : DynamicFormView.Callback {
//            override fun onCancelClicked() {
//                viewState = ViewState.ChatBot
//            }
//
//            override fun onSendClicked(data: DynamicForm) {
//                logDebug("onSendClicked: $data")
//
//                dynamicFormView.resetData()
//
//                socket?.emit("form_final")
//                    ?.on("form_final") { args ->
//                        logDebug("event [FORM_FINAL]: $args")
//
//                        if (args.size != 1) return@on
//
//                        val formFinalJson = args[0] as? JSONObject? ?: return@on
//                        logDebug("formFinalJson: $formFinalJson")
//                    }
//            }
//        }

        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (isUserPromptMode && bottom < oldBottom) {
                recyclerView.postDelayed(
                    { mergeAdapter?.let { recyclerView.scrollToPosition(it.itemCount - 1) } },
                    1
                )
            }
        }

        rootView.viewTreeObserver?.addOnGlobalLayoutListener {
            val rec = Rect()
            rootView.getWindowVisibleDisplayFrame(rec)

            // finding screen height
            val screenHeight = rootView.rootView?.height ?: 0

            // finding keyboard height
            val keypadHeight = screenHeight - rec.bottom

            if (keypadHeight > screenHeight * 0.15) {
                bottomNavigationView.hideButtons()
            } else {
                bottomNavigationView.showButtons()
            }
        }

        footerView.callback = object : FooterView.Callback {
            override fun onGoToActiveDialogButtonClicked() {
                setNewStateByPreviousState(State.SHOWN)
            }

            override fun onAttachmentButtonClicked() {
                if (footerView.isAttachmentButtonEnabled) {
                    MaterialFilePicker()
                        .withActivity(this@KenesWidgetV2Activity)
                        .withHiddenFiles(true)
                        .withFilterDirectories(false)
                        .withCloseMenu(true)
                        .withRequestCode(FILE_PICKER_REQUEST_CODE)
                        .start()
                } else {
                    showAddAttachmentButtonDisabledAlert {}
                }
            }

            override fun onInputViewFocusChangeListener(v: View, hasFocus: Boolean) {
                if (!hasFocus) hideKeyboard(v)
            }

            override fun onInputViewClicked() {
//                scrollToBottom()
            }

            override fun onSendMessageButtonClicked(message: String) {
                if (message.isNotBlank()) {
                    isUserPromptMode = true
                    sendUserMessage(message, true)
//                    chatAdapter?.notifyDataSetChanged()

                    isLoading = true
                }
            }
        }

        footerView.setOnInputViewFocusChangeListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val text = v?.text.toString()

                if (text.isBlank()) {
                    return@setOnInputViewFocusChangeListener false
                }

                isUserPromptMode = true
                sendUserMessage(text, true)
//                chatAdapter?.notifyDataSetChanged()

                isLoading = true

                return@setOnInputViewFocusChangeListener true
            }
            return@setOnInputViewFocusChangeListener false
        }

        footerView.setOnTextChangedListener { s, _, _, _ ->
            if (s.isNullOrBlank()) {
                footerView.disableSendMessageButton()
            } else {
                footerView.enableSendMessageButton()
            }
        }

        videoDialogView.callback = object : VideoDialogView.Callback {
            override fun onGoToChatButtonClicked() {
                viewState = ViewState.VideoDialog(State.HIDDEN)
            }

            override fun onHangupButtonClicked() {
                showHangupConfirmAlert { hangupLiveCall() }
            }

            override fun onSwitchSourceButtonClicked() {
                (localVideoCapturer as? CameraVideoCapturer?)?.switchCamera(null)
            }

            override fun onRemoteFrameClicked() {
                if (videoDialogView.isControlButtonsVisible()) {
                    videoDialogView.hideControlButtons()
                } else {
                    videoDialogView.showControlButtons()
                }
            }
        }

        audioDialogView.callback = object : AudioDialogView.Callback {
            override fun onGoToChatButtonClicked() {
                viewState = ViewState.AudioDialog(State.HIDDEN)
            }

            override fun onHangupButtonClicked() {
                showHangupConfirmAlert { hangupLiveCall() }
            }
        }

        infoView.callback = object : InfoView.Callback {
            override fun onPhoneNumberClicked(phoneNumber: String) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(intent)
            }

            override fun onSocialClicked(contact: Configs.Contact) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(contact.url))
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }

            override fun onLanguageChangeClicked(language: Language) {
                val languages = Language.AllLanguages
                val items = languages.map { it.value }.toTypedArray()
                showLanguageSelectionAlert(items) { which ->
                    val selected = languages[which]

                    socketClient?.sendUserLanguage(selected.key)

                    setLanguage(selected.locale)
                }
            }
        }

        setupRecyclerView()

        setKeyboardBehavior()

        fetchWidgetConfigs()
        fetchIceServers()

        connectToSignallingServer()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(object : ChatAdapter.Callback {
            override fun onShowAllCategoryChildClicked(category: Category) {
                chatFooterAdapter?.showGoToHomeButton()

                chatBot.activeCategory = category

                val messages = listOf(Message(
                    type = Message.Type.CROSS_CHILDREN,
                    category = chatBot.activeCategory
                ))
                runOnUiThread {
                    chatAdapter?.setNewMessages(messages)
                }
            }

            override fun onCategoryChildClicked(category: Category) {
                hideKeyboard()

                chatBot.activeCategory = category

                chatRecyclerState = recyclerView.layoutManager?.onSaveInstanceState()

                chatFooterAdapter?.showGoToHomeButton()

                if (category.responses.isNotEmpty()) {
                    socketClient?.requestResponse(category.responses.first(), currentLanguage)
                } else {
                    socketClient?.requestCategories(category.id, currentLanguage)
                }

                isLoading = true
            }

            override fun onGoBackClicked(category: Category) {
                hideKeyboard()

                val categories = chatBot.allCategories.filter { it.id == category.parentId }

                val messages = if (categories.all { it.parentId == null }) {
                    runOnUiThread {
                        chatFooterAdapter?.clear()
                    }

                    chatBot.basicCategories.map { Message(type = Message.Type.CATEGORY, category = it) }
                } else {
                    categories.map { Message(type = Message.Type.CROSS_CHILDREN, category = it) }
                }

                chatBot.activeCategory = null

                runOnUiThread {
                    chatAdapter?.setNewMessages(messages)
                }

                chatRecyclerState?.let { chatRecyclerState ->
                    recyclerView.layoutManager?.onRestoreInstanceState(chatRecyclerState)
                }
            }

            override fun onUrlInTextClicked(url: String) {
                if (url.startsWith("#")) {
                    chatFooterAdapter?.showGoToHomeButton()

                    val text = url.removePrefix("#")
                    isUserPromptMode = true
                    sendUserMessage(text, false)
//                    chatAdapter?.notifyDataSetChanged()

                    isLoading = true
                } else {
                    showOpenLinkConfirmAlert(url) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun onImageClicked(imageView: ImageView, imageUrl: String) {
                imageView.showFullscreenImage(imageUrl)
            }

            override fun onImageClicked(imageView: ImageView, bitmap: Bitmap) {
                imageView.showFullscreenImage(bitmap)
            }

            override fun onImageLoadCompleted() {
            }

            override fun onMediaClicked(media: Media, position: Int) {
                val file = media.getFile(this@KenesWidgetV2Activity)
                if (file.exists()) {
                    file.openFile(this@KenesWidgetV2Activity)
                } else {
                    try {
                        file.downloadFile(position, media.fileUrl, "media") {}
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onAttachmentClicked(attachment: Attachment, position: Int) {
                val file = attachment.getFile(this@KenesWidgetV2Activity)
                if (file.exists()) {
                    file.openFile(this@KenesWidgetV2Activity)
                } else {
                    try {
                        file.downloadFile(position, attachment.url, "attachment") {
                            file.openFile(this@KenesWidgetV2Activity)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })

        recyclerView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return EdgeEffect(view.context)
                    .apply { color = ContextCompat.getColor(this@KenesWidgetV2Activity, R.color.kenes_light_blue) }
            }
        }
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
//        layoutManager.stackFromEnd = true
        recyclerView.layoutManager = layoutManager
        recyclerView.isNestedScrollingEnabled = false

        chatAdapter?.registerAdapterDataObserver(chatAdapterDataObserver)
        chatFooterAdapter?.registerAdapterDataObserver(chatFooterAdapterDataObserver)

        chatFooterAdapter = ChatFooterAdapter()
        chatFooterAdapter?.callback = object : ChatFooterAdapter.Callback {
            override fun onGoToHomeClicked() {
                hideKeyboard()

                isUserPromptMode = false

                when (viewState) {
                    is ViewState.VideoDialog -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                            chatAdapter?.clear()
                        }

                        viewState = ViewState.VideoDialog(State.IDLE)
                    }
                    is ViewState.AudioDialog -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                            chatAdapter?.clear()
                        }

                        viewState = ViewState.AudioDialog(State.IDLE)
                    }
                    else -> {
                        val messages = chatBot.basicCategories.map { category ->
                            Message(type = Message.Type.CATEGORY, category = category)
                        }

                        runOnUiThread {
                            chatFooterAdapter?.clear()
                            chatAdapter?.setNewMessages(messages)
                        }

                        chatRecyclerState?.let { chatRecyclerState ->
                            recyclerView.layoutManager?.onRestoreInstanceState(chatRecyclerState)
                        }

                        viewState = ViewState.ChatBot
                    }
                }
            }

            override fun onSwitchToCallAgentClicked() {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        socketClient?.forceDisconnect()

                        dialog.isInitiator = false

                        viewState = ViewState.ChatBot
                    }
                    return
                }

                dialog.isInitiator = true

                dialog.isSwitchToCallAgentClicked = true

                chatFooterAdapter?.clear()

                socketClient?.textCallToCallAgent(currentLanguage)
            }

            override fun onRegisterAppealClicked() {
                viewState = ViewState.RegisterForm
            }
        }

        mergeAdapter = MergeAdapter(chatAdapter, chatFooterAdapter)

        recyclerView.adapter = mergeAdapter

        recyclerView.itemAnimator = null

        recyclerView.addItemDecoration(ChatAdapterItemDecoration(this))
    }

    private fun File.downloadFile(position: Int, url: String?, fileType: String, callback: () -> Unit) {
        if (url.isNullOrBlank()) return
        httpClient.downloadFile(this@KenesWidgetV2Activity, this, url) { downloadResult ->
            when (downloadResult) {
                is DownloadResult.Success -> {
                    callback()
                    chatAdapter?.setDownloading(position, Message.File.DownloadStatus.COMPLETED)
                }
                is DownloadResult.Error ->
                    chatAdapter?.setDownloading(position, Message.File.DownloadStatus.ERROR)
                is DownloadResult.Progress ->
                    chatAdapter?.setProgress(position, fileType, downloadResult.progress)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setKeyboardBehavior() {
        recyclerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                hideKeyboard()
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions(): Boolean {
        val permissions = permissionsRequest.checkStatus()
        if (permissions.anyShouldShowRationale() || permissions.anyDenied() || permissions.anyPermanentlyDenied()) {
            permissionsRequest.send()
            return true
        }
        return false
    }

    override fun onPermissionsResult(result: List<PermissionStatus>) {
        if (result.allGranted()) {
            return
        }
        showPermanentlyDeniedDialog(getString(R.string.kenes_permissions_necessity_info)) { isPositive ->
            if (isPositive) {
                if (result.anyPermanentlyDenied()) {
                    val intent = createAppSettingsIntent()
                    startActivity(intent)
                } else if (result.anyShouldShowRationale()) {
                    permissionsRequest.send()
                }
            } else {
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val filePath = data?.getStringExtra(FilePickerActivity.RESULT_FILE_PATH) ?: return

            val file = File(filePath)
            val type = file.getFileType() ?: return

            val params = RequestParams().apply {
                put("type", type)
                put("file", file)
            }

            httpClient.uploadFile(UrlUtil.buildUrl("/upload"), params) { path, hash ->
                logDebug("uploadFile: $path, $hash")

                socketClient?.sendUserMediaMessage(type, path)

                val fullUrl = UrlUtil.buildUrl(path)

                val media = if (type == "image") {
                    Media(
                        imageUrl = fullUrl,
                        hash = hash,
                        ext = hash.split(".").last(),
                        local = file
                    )
                } else {
                    Media(
                        fileUrl = fullUrl,
                        hash = hash,
                        ext = hash.split(".").last(),
                        local = file
                    )
                }

                runOnUiThread {
                    chatAdapter?.addNewMessage(Message(type = Message.Type.USER, media = media))
//                    scrollToBottom()
                }
            }
        }
    }

    override fun onBackPressed() {
        showWidgetCloseConfirmDialog { finish() }
    }

    private fun initializeCallConnection(isVideoCall: Boolean = true) {
        rootEglBase = EglBase.create()

        if (isVideoCall) {
            runOnUiThread {
                videoDialogView.localSurfaceView.init(rootEglBase?.eglBaseContext, null)
                videoDialogView.localSurfaceView.setEnableHardwareScaler(true)
                videoDialogView.localSurfaceView.setMirror(false)
                videoDialogView.localSurfaceView.setZOrderMediaOverlay(true)
                videoDialogView.localSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)

                videoDialogView.remoteSurfaceView.init(rootEglBase?.eglBaseContext, null)
                videoDialogView.remoteSurfaceView.setEnableHardwareScaler(true)
                videoDialogView.remoteSurfaceView.setMirror(true)
                videoDialogView.remoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            }
        }

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .createInitializationOptions()

        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        options.disableNetworkMonitor = true

//        val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
//            .setUseHardwareAcousticEchoCanceler(true)
//            .setUseHardwareNoiseSuppressor(true)
//            .setAudioRecordErrorCallback(null)
//            .setAudioTrackErrorCallback(null)
//            .setSamplesReadyCallback(null)
//            .createAudioDeviceModule()

        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase?.eglBaseContext,  /* enableIntelVp8Encoder */
            true,  /* enableH264HighProfile */
            true
        )

        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
//            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

//        if (isVideoCall) {
//            peerConnectionFactory?.setVideoHwAccelerationOptions(
//                rootEglBase?.eglBaseContext,
//                rootEglBase?.eglBaseContext
//            )
//        }

        peerConnection = peerConnectionFactory?.let { createPeerConnection(it) }

        localMediaStream = peerConnectionFactory?.createLocalMediaStream("ARDAMS")

        if (isVideoCall) {
            localMediaStream?.addTrack(createVideoTrack())
        }

        localMediaStream?.addTrack(createAudioTrack())

        peerConnection?.addStream(localMediaStream)

        runOnUiThread {
            audioManager = AppRTCAudioManager.create(this)
            audioManager?.start { selectedAudioDevice, availableAudioDevices ->
                logDebug("onAudioManagerDevicesChanged: $availableAudioDevices, selected: $selectedAudioDevice")
            }
        }

        previousVolumeControlStream = volumeControlStream

        volumeControlStream = AudioManager.STREAM_VOICE_CALL
    }

    private fun createVideoTrack(): VideoTrack? {
        localVideoCapturer = createVideoCapturer()
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase?.eglBaseContext)
        localVideoSource = peerConnectionFactory?.createVideoSource(false)
        localVideoCapturer?.initialize(surfaceTextureHelper, applicationContext, localVideoSource?.capturerObserver)
        localVideoCapturer?.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)

        localVideoTrack = peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, localVideoSource)
        localVideoTrack?.setEnabled(true)
//        localVideoTrack?.addRenderer(VideoRenderer(videoDialogView.localSurfaceView))
        localVideoTrack?.addSink(ProxyVideoSink(TAG).also { it.setTarget(videoDialogView.localSurfaceView) })

        return localVideoTrack
    }

    private fun createAudioTrack(): AudioTrack? {
        localAudioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, localAudioSource)
        localAudioTrack?.setEnabled(true)
        return localAudioTrack
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // find the front facing camera and return it.
        deviceNames
            .filter { enumerator.isFrontFacing(it) }
            .mapNotNull { enumerator.createCapturer(it, null) }
            .forEach { return it }
        return null
    }

    private fun useCamera2(): Boolean = Camera2Enumerator.isSupported(this)

    private fun fetchWidgetConfigs() {
        val task = WidgetConfigsTask(UrlUtil.getHostname() + "/configs")

        val data = task.run()

        data?.let {
            configs = data

            infoView.setContacts(configs.contacts)
            infoView.setPhones(configs.phones)
            infoView.setLanguage(Language.from(getCurrentLanguage()))

            headerView.setOpponentInfo(configs.opponent)
        }
    }

    private fun fetchIceServers() {
        val task = IceServersTask(UrlUtil.getHostname() + "/ice_servers")

        val data = task.run()

        data?.let {
            iceServers = data
        }
    }

    private fun connectToSignallingServer() {
        val signallingServerUrl = UrlUtil.getSignallingServerUrl()
        if (signallingServerUrl.isNullOrBlank()) {
            throw NullPointerException("Signalling server url is null. Please, provide a valid url.")
        } else {
            socketClient = SocketClient(signallingServerUrl, currentLanguage)
        }

        socketClient?.listener = object : SocketClient.Listener {
            override fun onSocketConnect() {}

            override fun onCall(type: String, media: String, operator: String, instance: String) {
                if (type == "accept") {
                    dialog = Dialog(operator, instance, media)

                    runOnUiThread {
                        chatAdapter?.clearCategoryMessages()

                        footerView.disableAttachmentButton()
                    }

                    if (media == "audio") {
                        viewState = ViewState.AudioDialog(State.PREPARATION)
                    } else if (media == "video") {
                        viewState = ViewState.VideoDialog(State.PREPARATION)
                    }
                }
            }

            override fun onCallAgentGreet(fullName: String, photoUrl: String?, text: String) {
                if (dialog.isSwitchToCallAgentClicked) {
                    dialog = Dialog(operatorId = fullName, media = "text")

                    runOnUiThread {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                        headerView.showHangupButton()

                        chatFooterAdapter?.clear()

                        footerView.enableAttachmentButton()
                    }
                }

                val formattedText = text.replace("{}", fullName)

                runOnUiThread {
                    headerView.setOpponentInfo(Configs.Opponent(
                        name = fullName,
                        secondName = getString(R.string.kenes_call_agent),
                        avatarUrl = photoUrl
                    ))

                    if (viewState is ViewState.AudioDialog) {
                        audioDialogView.setAvatar(photoUrl)
                        audioDialogView.setName(fullName)
                    }

                    chatAdapter?.addNewMessage(Message(Message.Type.OPPONENT, formattedText))
//                    scrollToBottom()
                }
            }

            override fun onFormInit(dynamicForm: DynamicForm) {}

            override fun onFeedback(text: String, ratingButtons: List<RatingButton>) {
                closeLiveCall()

                viewState = ViewState.CallFeedback

                runOnUiThread {
                    hideKeyboard(footerView.inputView)

                    feedbackView.setTitle(text)
                    feedbackView.setRatingButtons(ratingButtons)
                    feedbackView.setOnRateButtonClickListener { ratingButton ->
                        socketClient?.sendFeedback(ratingButton)

                        if (!setNewStateByPreviousState(State.FINISHED)) {
                            viewState = ViewState.ChatBot
                        }
                    }
                }
            }

            override fun onPendingUsersQueueCount(text: String?, count: Int) {
                runOnUiThread {
                    if (viewState is ViewState.VideoDialog) {
                        if (text != null) {
                            videoCallView.setInfoText(text)
                        }
                        if (count > 1) {
                            videoCallView.setPendingQueueCount(count)
                        }
                    } else if (viewState is ViewState.AudioDialog) {
                        if (text != null) {
                            audioCallView.setInfoText(text)
                        }
                        if (count > 1) {
                            audioCallView.setPendingQueueCount(count)
                        }
                    }
                }
            }

            override fun onNoResultsFound(text: String, timestamp: Long): Boolean {
                runOnUiThread {
                    chatAdapter?.addNewMessage(Message(
                        type = Message.Type.OPPONENT,
                        text = text,
                        timestamp = timestamp
                    ))
                    chatFooterAdapter?.showSwitchToCallAgentButton()
//                    scrollToBottom()
                }

                isLoading = false

                return true
            }

            override fun onFuzzyTaskOffered(text: String, timestamp: Long): Boolean {
                runOnUiThread {
                    chatAdapter?.addNewMessage(Message(
                        type = Message.Type.OPPONENT,
                        text = text,
                        timestamp = timestamp
                    ))
                    chatFooterAdapter?.showFuzzyQuestionButtons()
                }

                isLoading = false

                return true
            }

            override fun onNoOnlineCallAgents(text: String): Boolean {
                dialog.isInitiator = false

                runOnUiThread {
                    chatAdapter?.addNewMessage(Message(Message.Type.OPPONENT, text))

                    showNoOnlineCallAgents(text) {
                        isLoading = false
                        setNewStateByPreviousState(State.IDLE)
                    }
                }

                return true
            }

            override fun onCallAgentDisconnected(text: String, timestamp: Long): Boolean {
                closeLiveCall()

                setNewStateByPreviousState(State.OPPONENT_DISCONNECT)

                runOnUiThread {
                    chatAdapter?.addNewMessage(Message(
                        type = Message.Type.NOTIFICATION,
                        text = text,
                        timestamp = timestamp
                    ))
//                    scrollToBottom()
                }

                return true
            }

            override fun onRtcStart() {
                runOnUiThread {
                    headerView.showHangupButton()
                    footerView.visibility = View.VISIBLE
                }

                socketClient?.sendMessage(
                    rtc = rtc { type = Rtc.Type.PREPARE },
                    language = currentLanguage
                )
            }

            override fun onRtcPrepare() {
                if (viewState is ViewState.VideoDialog) {
                    viewState = ViewState.VideoDialog(State.PREPARATION)

                    initializeCallConnection(isVideoCall = true)

                    socketClient?.sendMessage(
                        rtc = rtc { type = Rtc.Type.READY },
                        language = currentLanguage
                    )
                } else if (viewState is ViewState.AudioDialog) {
                    viewState = ViewState.AudioDialog(State.PREPARATION)

                    initializeCallConnection(isVideoCall = false)

                    socketClient?.sendMessage(
                        rtc = rtc { type = Rtc.Type.READY },
                        language = currentLanguage
                    )
                }
            }

            override fun onRtcReady() {
                if (viewState is ViewState.VideoDialog) {
                    viewState = ViewState.VideoDialog(State.LIVE)

                    initializeCallConnection(isVideoCall = true)

                    sendOffer()
                } else if (viewState is ViewState.AudioDialog) {
                    viewState = ViewState.AudioDialog(State.LIVE)

                    initializeCallConnection(isVideoCall = false)

                    sendOffer()
                }
            }

            override fun onRtcOffer(type: SessionDescription.Type?, sessionDescription: String) {
                setNewStateByPreviousState(State.LIVE)

                setRemoteDescription(type, sessionDescription)

                sendAnswer()
            }

            override fun onRtcAnswer(type: SessionDescription.Type?, sessionDescription: String) {
                setRemoteDescription(type, sessionDescription)
            }

            override fun onRtcIceCandidate(iceCandidate: IceCandidate) {
                peerConnection?.addIceCandidate(iceCandidate)
            }

            override fun onRtcHangup() {
                closeLiveCall()

                setNewStateByPreviousState(State.IDLE)
            }

            override fun onTextMessage(text: String, attachments: List<Attachment>?, timestamp: Long) {
                if (chatBot.activeCategory != null) {
                    val messages = listOf(
                        Message(
                            type = Message.Type.RESPONSE,
                            text = text,
                            attachments = attachments,
                            timestamp = timestamp,
                            category = chatBot.activeCategory
                        )
                    )

                    runOnUiThread {
                        chatFooterAdapter?.showGoToHomeButton()
                        chatAdapter?.setNewMessages(messages)
                    }
                    isLoading = false
                    return
                }

                if (dialog.isOnLive) {
                    runOnUiThread {
                        chatAdapter?.addNewMessage(Message(
                            type = Message.Type.OPPONENT,
                            text = text,
                            attachments = attachments,
                            timestamp = timestamp
                        ))
//                        scrollToBottom()
                    }
                } else {
                    runOnUiThread {
                        chatAdapter?.addNewMessage(Message(
                            type = Message.Type.OPPONENT,
                            text = text,
                            attachments = attachments,
                            timestamp = timestamp
                        ))

                        if (!dialog.isSwitchToCallAgentClicked) {
                            chatFooterAdapter?.showGoToHomeButton()
                        }

//                        scrollToBottom()
                    }

                    isLoading = false
                }
            }

            override fun onMediaMessage(media: Media, timestamp: Long) {
                if (media.isImage) {
                    runOnUiThread {
                        chatAdapter?.addNewMessage(Message(
                            type = Message.Type.OPPONENT,
                            media = media,
                            timestamp = timestamp
                        ))
//                        scrollToBottom()
                    }
                }

                if (media.isFile) {
                    runOnUiThread {
                        chatAdapter?.addNewMessage(Message(
                            type = Message.Type.OPPONENT,
                            media = media,
                            timestamp = timestamp
                        ))
//                        scrollToBottom()
                    }
                }
            }

            override fun onCategories(categories: List<Category>) {
                if (chatBot.isLocked) return

                val sortedCategories = categories.sortedBy { it.id }
                chatBot.allCategories.addAll(sortedCategories)

                if (!chatBot.isBasicCategoriesFilled) {
                    chatBot.allCategories.forEach { category ->
//                    logDebug("category: $category, ${category.parentId == null}")

                        if (category.parentId == null) {
                            socketClient?.requestCategories(category.id, currentLanguage)
                        }
                    }

                    chatBot.isBasicCategoriesFilled = true
                }

                if (chatBot.activeCategory != null) {
                    if (chatBot.activeCategory?.children?.containsAll(sortedCategories) == false) {
                        chatBot.activeCategory?.children?.addAll(sortedCategories)
                    }
                    val messages = listOf(
                        Message(
                            type = Message.Type.CROSS_CHILDREN,
                            category = chatBot.activeCategory
                        )
                    )
                    runOnUiThread {
                        chatAdapter?.setNewMessages(messages)
                    }
                }

                isLoading = false
            }

            override fun onSocketDisconnect() {
                closeLiveCall()

                if (viewState is ViewState.VideoDialog || viewState is ViewState.AudioDialog) {
                    setNewStateByPreviousState(State.IDLE)
                } else {
                    viewState = ViewState.ChatBot
                }
            }
        }
    }


    private fun setLocalDescription(type: SessionDescription.Type?, description: String) {
        val newDescription = CodecUtil.preferCodec2(description, CodecUtil.AUDIO_CODEC_ISAC, true)

        peerConnection?.setLocalDescription(
            SimpleSdpObserver(),
            SessionDescription(type, newDescription)
        )
    }

    private fun setRemoteDescription(type: SessionDescription.Type?, description: String) {
        val newDescription = CodecUtil.preferCodec2(description, CodecUtil.AUDIO_CODEC_OPUS, true)

//        newSdpDescription = Codec.setStartBitrate(Codec.AUDIO_CODEC_OPUS, false, newSdpDescription, 32)

        peerConnection?.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(type, newDescription)
        )
    }

    private fun sendAnswer() {
        val mediaConstraints = MediaConstraints()

//        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
//        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                logDebug("onCreateSuccess")

                setLocalDescription(sessionDescription.type, sessionDescription.description)

                socketClient?.sendMessage(
                    rtc = rtc { type = Rtc.Type.ANSWER; sdp = sessionDescription.description},
                    language = currentLanguage
                )
            }
        }, mediaConstraints)
    }

    private fun sendOffer() {
        val mediaConstraints = MediaConstraints()

//        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
//        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                logDebug("onCreateSuccess")

                setLocalDescription(sessionDescription.type, sessionDescription.description)

                socketClient?.sendMessage(
                    rtc = rtc { type = Rtc.Type.OFFER; sdp = sessionDescription.description},
                    language = currentLanguage
                )
            }

            override fun onCreateFailure(s: String) {
                super.onCreateFailure(s)
                logDebug("onCreateFailure: $s")
            }
        }, mediaConstraints)
    }

    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection? {
        var iceServers = iceServers.map {
            PeerConnection.IceServer.builder(it.url)
                .setUsername(it.username)
                .setPassword(it.credential)
                .createIceServer()
        }

        if (iceServers.isNullOrEmpty()) {
            iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                    .createIceServer()
            )
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY

        val peerConnectionObserver = object : PeerConnection.Observer {
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}

            override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                logDebug("onSignalingChange: $signalingState")
            }

            override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                logDebug("onIceConnectionChange: $iceConnectionState")

                when (iceConnectionState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        runOnUiThread {
                            footerView.enableAttachmentButton()
                        }
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        dialog.clear()

                        setNewStateByPreviousState(State.IDLE)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        hangupLiveCall()
                    }
                    else -> {
                    }
                }
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                logDebug("onIceConnectionReceivingChange: $b")
            }

            override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                logDebug("onIceGatheringChange: $iceGatheringState")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                logDebug("onIceCandidate: " + iceCandidate.sdp)

                socketClient?.sendMessage(
                    rtc = rtc {
                        type = Rtc.Type.CANDIDATE
                        id = iceCandidate.sdpMid
                        label = iceCandidate.sdpMLineIndex
                        candidate = iceCandidate.sdp
                    },
                    language = currentLanguage
                )
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                logDebug("onIceCandidatesRemoved: " + iceCandidates.contentToString())
            }

            override fun onAddStream(mediaStream: MediaStream) {
                logDebug("onAddStream -> mediaStream.audioTracks.size: " + mediaStream.audioTracks.size)
                logDebug("onAddStream -> mediaStream.videoTracks.size: " + mediaStream.videoTracks.size)

                if (mediaStream.audioTracks.isNotEmpty()) {
                    remoteAudioTrack = mediaStream.audioTracks[0]
                    remoteAudioTrack?.setEnabled(true)
//                    setSpeakerphoneOn(true)
                }

                if (viewState is ViewState.VideoDialog) {
                    if (mediaStream.videoTracks.isNotEmpty()) {
                        remoteVideoTrack = mediaStream.videoTracks[0]
                        remoteVideoTrack?.setEnabled(true)
//                        remoteVideoTrack?.addRenderer(VideoRenderer(videoDialogView.remoteSurfaceView))
                        remoteVideoTrack?.addSink(ProxyVideoSink(TAG).also { it.setTarget(videoDialogView.remoteSurfaceView) })
                    }
                }
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                logDebug("onRemoveStream: $mediaStream")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                logDebug("onDataChannel: $dataChannel")
            }

            override fun onRenegotiationNeeded() {
                logDebug("onRenegotiationNeeded")
                if (dialog.isInitiator) {
                    sendOffer()
                } else {
                    sendAnswer()
                }
            }
        }
        return factory.createPeerConnection(rtcConfig, peerConnectionObserver)
    }

    private fun hangupLiveCall() {
        if (dialog.media == "text") {
            dialog.isSwitchToCallAgentClicked = false

            dialog.clear()

            runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                footerView.disableAttachmentButton()

                chatAdapter?.addNewMessage(Message(Message.Type.NOTIFICATION, getString(R.string.kenes_user_disconnected)))
            }

            socketClient?.sendMessage(
                action = UserMessage.Action.FINISH,
                language = currentLanguage
            )
        } else {
            dialog.clear()

            runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                footerView.disableAttachmentButton()

                chatAdapter?.addNewMessage(Message(Message.Type.NOTIFICATION, getString(R.string.kenes_user_disconnected)))
            }

            volumeControlStream = previousVolumeControlStream

            socketClient?.sendMessage(
                rtc = rtc { type = Rtc.Type.HANGUP },
                language = currentLanguage
            )

            socketClient?.sendMessage(
                action = UserMessage.Action.FINISH,
                language = currentLanguage
            )
        }
    }

    private fun sendUserMessage(message: String, isInputClearText: Boolean = true) {
        socketClient?.sendUserMessage(message, currentLanguage)

        if (isInputClearText) {
            footerView.clearInputViewText()
        }

        chatAdapter?.addNewMessage(Message(Message.Type.USER, message))
//        scrollToBottom()
    }

//    private fun scrollToTop() {
//        if ((recyclerView.layoutManager as? LinearLayoutManager?)?.findFirstCompletelyVisibleItemPosition() == 0) {
//            return
//        }
//        recyclerView.scrollToPosition(0)
//    }

//    private fun scrollToBottom() {
//        val adapter = recyclerView.adapter
//        if (adapter != null) {
//            recyclerView.scrollToPosition(adapter.itemCount - 1)
//        }
//    }

    private fun setNewStateByPreviousState(state: State): Boolean {
        return when (viewState) {
            is ViewState.VideoDialog -> {
                viewState = ViewState.VideoDialog(state)
                true
            }
            is ViewState.AudioDialog -> {
                viewState = ViewState.AudioDialog(state)
                true
            }
            else -> false
        }
    }

    private fun updateViewState(viewState: ViewState) {
        when (viewState) {
            is ViewState.VideoDialog -> {
                infoView.visibility = View.GONE

                chatFooterAdapter?.clear()

                if (isLoading) {
                    isLoading = false
                }

                when (viewState.state) {
                    State.IDLE, State.USER_DISCONNECT -> {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                        headerView.hideHangupButton()
                        headerView.setOpponentInfo(configs.opponent)

                        audioCallView.setDefaultState()
                        audioCallView.visibility = View.GONE

                        audioDialogView.setDefaultState()
                        audioDialogView.visibility = View.GONE

                        videoDialogView.setDefaultState()
                        videoDialogView.visibility = View.GONE

                        recyclerView.visibility = View.GONE

                        footerView.setGoToActiveDialogButtonState(null)
                        footerView.visibility = View.GONE

                        bottomNavigationView.setNavButtonsEnabled()
                        bottomNavigationView.setVideoNavButtonActive()

                        videoCallView.setDefaultState()
                        videoCallView.visibility = View.VISIBLE
                    }
                    State.PENDING -> {
                        headerView.hideHangupButton()

                        videoCallView.setDisabledState()

//                        chatAdapter?.clearMessages()
                    }
                    State.PREPARATION -> {
                        headerView.hideHangupButton()

                        videoCallView.setDisabledState()
                        videoCallView.visibility = View.GONE

                        feedbackView.setDefaultState()
                        feedbackView.visibility = View.GONE

                        recyclerView.visibility = View.VISIBLE
                    }
                    State.LIVE -> {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                        headerView.showHangupButton()

                        videoCallView.setDisabledState()
                        videoCallView.visibility = View.GONE

                        feedbackView.setDefaultState()
                        feedbackView.visibility = View.GONE

                        bottomNavigationView.setNavButtonsDisabled()

                        recyclerView.visibility = View.VISIBLE

                        footerView.visibility = View.VISIBLE

                        videoDialogView.visibility = View.VISIBLE
                    }
                    State.OPPONENT_DISCONNECT, State.FINISHED -> {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                        headerView.hideHangupButton()
                        headerView.setOpponentInfo(configs.opponent)

                        videoCallView.setDefaultState()
                        videoCallView.visibility = View.GONE

                        feedbackView.visibility = View.GONE

                        footerView.setDefaultState()
                        footerView.visibility = View.GONE

                        bottomNavigationView.setNavButtonsEnabled()

                        recyclerView.visibility = View.VISIBLE
                    }
                    State.HIDDEN -> {
                        headerView.showHangupButton()

                        recyclerView.visibility = View.VISIBLE
//                        scrollToBottom()

                        footerView.setGoToActiveDialogButtonState(R.string.kenes_return_to_video_call)

                        videoDialogView.visibility = View.INVISIBLE
                    }
                    State.SHOWN -> {
                        headerView.hideHangupButton()

                        hideKeyboard(footerView.inputView)

                        footerView.setGoToActiveDialogButtonState(null)

                        videoDialogView.showControlButtons()
                        videoDialogView.visibility = View.VISIBLE
                    }
                }
            }
            is ViewState.AudioDialog -> {
                infoView.visibility = View.GONE

                chatFooterAdapter?.clear()

                if (isLoading) {
                    isLoading = false
                }

                when (viewState.state) {
                    State.IDLE, State.USER_DISCONNECT -> {
                        headerView.hideHangupButton()
                        headerView.setOpponentInfo(configs.opponent)

                        videoCallView.setDefaultState()
                        videoCallView.visibility = View.GONE

                        audioDialogView.setDefaultState()
                        audioDialogView.visibility = View.GONE

                        videoDialogView.setDefaultState()
                        videoDialogView.visibility = View.GONE

                        recyclerView.visibility = View.GONE

                        footerView.setGoToActiveDialogButtonState(null)
                        footerView.visibility = View.GONE

                        bottomNavigationView.setNavButtonsEnabled()
                        bottomNavigationView.setAudioNavButtonActive()

                        audioCallView.setDefaultState()
                        audioCallView.visibility = View.VISIBLE
                    }
                    State.PENDING -> {
                        headerView.hideHangupButton()

                        audioCallView.setDisabledState()
                    }
                    State.PREPARATION -> {
                        headerView.hideHangupButton()

                        audioCallView.setDisabledState()
                        audioCallView.visibility = View.GONE

                        feedbackView.setDefaultState()
                        feedbackView.visibility = View.GONE

                        recyclerView.visibility = View.VISIBLE
                    }
                    State.LIVE -> {
                        headerView.showHangupButton()

                        audioCallView.setDisabledState()
                        audioCallView.visibility = View.GONE

                        feedbackView.visibility = View.GONE

                        bottomNavigationView.setNavButtonsDisabled()

                        recyclerView.visibility = View.VISIBLE

                        footerView.visibility = View.VISIBLE

                        audioDialogView.visibility = View.VISIBLE
                    }
                    State.OPPONENT_DISCONNECT, State.FINISHED -> {
                        headerView.hideHangupButton()
                        headerView.setOpponentInfo(configs.opponent)

                        audioCallView.setDefaultState()
                        audioCallView.visibility = View.GONE

                        feedbackView.visibility = View.GONE

                        footerView.setDefaultState()
                        footerView.visibility = View.GONE

                        bottomNavigationView.setNavButtonsEnabled()

                        recyclerView.visibility = View.VISIBLE
                    }
                    State.HIDDEN -> {
                        headerView.showHangupButton()

                        recyclerView.visibility = View.VISIBLE
//                        scrollToBottom()

                        footerView.setGoToActiveDialogButtonState(R.string.kenes_return_to_audio_call)

                        audioDialogView.visibility = View.INVISIBLE
                    }
                    State.SHOWN -> {
                        headerView.hideHangupButton()

                        hideKeyboard(footerView.inputView)

                        footerView.setGoToActiveDialogButtonState(null)

                        audioDialogView.visibility = View.VISIBLE
                    }
                }
            }
            ViewState.CallFeedback -> {
                chatFooterAdapter?.clear()

                headerView.hideHangupButton()

                audioCallView.setDisabledState()
                audioCallView.visibility = View.GONE

                videoCallView.setDisabledState()
                videoCallView.visibility = View.GONE

                audioDialogView.setDefaultState()
                audioDialogView.visibility = View.GONE

                videoDialogView.setDefaultState()
                videoDialogView.visibility = View.GONE

                infoView.visibility = View.GONE

                recyclerView.visibility = View.GONE

                footerView.setDefaultState()
                footerView.visibility = View.GONE

                bottomNavigationView.setNavButtonsDisabled()

                feedbackView.visibility = View.VISIBLE
            }
            ViewState.RegisterForm -> {
                recyclerView.visibility = View.GONE

                footerView.visibility = View.GONE

                bottomNavigationView.setNavButtonsDisabled()

                formView.visibility = View.VISIBLE
            }
            ViewState.DynamicFormFill -> {
                recyclerView.visibility = View.GONE

                footerView.visibility = View.GONE

                bottomNavigationView.setNavButtonsDisabled()

//                dynamicFormView.visibility = View.VISIBLE
            }
            ViewState.ChatBot -> {
                chatFooterAdapter?.clear()

                headerView.hideHangupButton()
                headerView.setOpponentInfo(configs.opponent)

                audioCallView.setDefaultState()
                audioCallView.visibility = View.GONE

                videoCallView.setDefaultState()
                videoCallView.visibility = View.GONE

                audioDialogView.setDefaultState()
                audioDialogView.visibility = View.GONE

                videoDialogView.setDefaultState()
                videoDialogView.visibility = View.GONE

                feedbackView.setDefaultState()
                feedbackView.visibility = View.GONE

                formView.visibility = View.GONE

//                dynamicFormView.visibility = View.GONE

                infoView.visibility = View.GONE

                bottomNavigationView.setNavButtonsEnabled()
                bottomNavigationView.setHomeNavButtonActive()

                if (!isLoading) {
                    recyclerView.visibility = View.VISIBLE
                }

                footerView.setDefaultState()
                footerView.visibility = View.VISIBLE
            }
            ViewState.Info -> {
                if (isLoading) {
                    isLoading = false
                }

                chatFooterAdapter?.clear()

                headerView.hideHangupButton()
                headerView.setOpponentInfo(configs.opponent)

                audioCallView.setDefaultState()
                audioCallView.visibility = View.GONE

                videoCallView.setDefaultState()
                videoCallView.visibility = View.GONE

                audioDialogView.setDefaultState()
                audioDialogView.visibility = View.GONE

                videoDialogView.setDefaultState()
                videoDialogView.visibility = View.GONE

                feedbackView.setDefaultState()
                feedbackView.visibility = View.GONE

                recyclerView.visibility = View.GONE

                footerView.setDefaultState()
                footerView.visibility = View.GONE

                bottomNavigationView.setInfoNavButtonActive()

                infoView.visibility = View.VISIBLE
            }
        }
    }

    private fun closeLiveCall() {
        dialog.clear()

        runOnUiThread {
            headerView.hideHangupButton()
            footerView.disableAttachmentButton()
        }

        dialog.isSwitchToCallAgentClicked = false

        peerConnection?.dispose()
        peerConnection = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        logDebug("Stopping capture.")
        try {
            localVideoCapturer?.stopCapture()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        localVideoCapturer?.dispose()
        localVideoCapturer = null

//        localVideoSource?.dispose()
        localVideoSource = null

//        localVideoSource?.dispose()
        localAudioSource = null

//        localMediaStream?.dispose()
        localMediaStream = null

        logDebug("Closing peer connection factory.")
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        logDebug("Closing peer connection done.")

//        PeerConnectionFactory.stopInternalTracingCapture()
//        PeerConnectionFactory.shutdownInternalTracer()

        rootEglBase?.release()
        rootEglBase = null

//        localVideoTrack?.dispose()
        localVideoTrack = null
//        remoteVideoTrack?.dispose()
        remoteVideoTrack = null

//        localAudioTrack?.dispose()
        localAudioTrack = null

//        remoteAudioTrack?.dispose()
        remoteAudioTrack = null

        videoDialogView.release()
    }

    override fun onDestroy() {
        super.onDestroy()

        footerView.disableAttachmentButton()

        isLoading = false

        isUserPromptMode = false

        viewState = ViewState.ChatBot

        closeLiveCall()

        iceServers = emptyList()

        configs.clear()
        chatBot.clear()
        dialog.clear()

        audioManager = null

        socketClient?.release()
        socketClient = null

//        headerView = null

        audioCallView.setDefaultState()
//        audioCallView = null

        audioDialogView.setDefaultState()
        audioDialogView.callback = null
//        audioDialogView = null

        videoCallView.setDefaultState()
//        videoCallView = null

        videoDialogView.setDefaultState()
        videoDialogView.callback = null
//        videoDialogView = null

        formView.clear()

        footerView.setDefaultState()
//        footerView = null

        chatRecyclerState = null

        chatAdapter?.callback = null
        chatAdapter?.clear()

        chatFooterAdapter?.callback = null
        chatFooterAdapter?.clear()

        chatAdapter?.let { mergeAdapter?.removeAdapter(it) }

        chatFooterAdapter?.let { mergeAdapter?.removeAdapter(it) }

        mergeAdapter = null

        try {
            chatAdapter?.unregisterAdapterDataObserver(chatAdapterDataObserver)
            chatFooterAdapter?.unregisterAdapterDataObserver(chatFooterAdapterDataObserver)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        chatAdapter = null

        chatFooterAdapter = null

        recyclerView.adapter = null
//        recyclerView = null

//        progressView = null

        bottomNavigationView.callback = null

//        rootView = null
    }

    private fun throwError() {
        Log.e(TAG,  getString(R.string.kenes_error_invalid_hostname))
        Toast.makeText(this, R.string.kenes_error_invalid_hostname, Toast.LENGTH_SHORT)
            .show()
        finish()
    }

    private fun logDebug(message: String) {
        if (message.length > 4000) {
            Log.d(TAG, message.substring(0, 4000))
            logDebug(message.substring(4000))
        } else {
            Log.d(TAG, message)
        }
    }

}