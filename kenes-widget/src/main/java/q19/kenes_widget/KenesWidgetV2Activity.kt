package q19.kenes_widget

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
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
import com.fondesa.kpermissions.PermissionStatus
import com.fondesa.kpermissions.allGranted
import com.fondesa.kpermissions.anyPermanentlyDenied
import com.fondesa.kpermissions.anyShouldShowRationale
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.request.PermissionRequest
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.RequestParams
import com.nbsp.materialfilepicker.MaterialFilePicker
import com.nbsp.materialfilepicker.ui.FilePickerActivity
import com.squareup.picasso.Picasso
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
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
import q19.kenes_widget.rtc.PeerConnectionClient
import q19.kenes_widget.ui.components.*
import q19.kenes_widget.util.*
import q19.kenes_widget.util.FileUtil.getFileType
import q19.kenes_widget.util.FileUtil.openFile
import q19.kenes_widget.util.Logger.debug
import java.io.File

class KenesWidgetV2Activity : LocalizationActivity(), PermissionRequest.Listener {

    companion object {
        private const val TAG = "KenesWidgetV2Activity"

        private const val KEY_HOSTNAME = "hostname"

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

    private val palette by lazy {
        try {
            resources.getIntArray(R.array.kenes_palette)
        } catch (e: Exception) {
            e.printStackTrace()
            intArrayOf()
        }
    }

    private var mergeAdapter: MergeAdapter? = null
    private var chatAdapter: ChatAdapter? = null
    private var chatFooterAdapter: ChatFooterAdapter? = null

    private var socketClient: SocketClient? = null

    private var peerConnectionClient: PeerConnectionClient? = null

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

    private var iceServers = listOf<PeerConnection.IceServer>()

    private var viewState: ViewState = ViewState.ChatBot.Category
        set(value) {
            field = value
            renderViewState(value)
        }

    private var isLoading: Boolean = false
        set(value) {
            if (field == value) return
            if (viewState is ViewState.TextDialog || viewState is ViewState.AudioDialog || viewState is ViewState.VideoDialog) return

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

        if (Picasso.get() == null) {
            Picasso.setSingletonInstance(Picasso.Builder(this).build())
        }

        // --------------------- [BEGIN] Default screen setups ----------------------------

        /**
         * Default active navigation button of [bottomNavigationView]
         */
        bottomNavigationView.setHomeNavButtonActive()

        isLoading = true

        /**
         * Default states of views
         */
        dialog.clear()

        headerView.hideHangupButton()
        headerView.setOpponentInfo(
            Configs.Opponent(
                "Kenes",
                "Smart Bot",
                drawableRes = R.drawable.kenes_ic_robot
            )
        )

        feedbackView.setDefaultState()
        footerView.setDefaultState()
        videoCallView.setDefaultState()
        audioCallView.setDefaultState()

        viewState = ViewState.ChatBot.Category

        // --------------------- [END] Default screen setups ----------------------------

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
            private fun reset() {
                chatBot.clear()

                chatAdapter?.clear()
                chatFooterAdapter?.clear()
            }

            override fun onHomeNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        cancelPendingCall()

                        viewState = ViewState.ChatBot.Category
                    }
                    return false
                }

                reset()

                socketClient?.getBasicCategories()
                isLoading = true

                viewState = ViewState.ChatBot.Category

                return true
            }

            override fun onVideoNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        cancelPendingCall()

                        viewState = ViewState.VideoDialog.IDLE
                    }
                    return false
                }

                reset()

                viewState = ViewState.VideoDialog.IDLE

                return true
            }

            override fun onAudioNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        cancelPendingCall()

                        viewState = ViewState.AudioDialog.IDLE
                    }
                    return false
                }

                reset()

                viewState = ViewState.AudioDialog.IDLE

                return true
            }

            override fun onInfoNavButtonClicked(): Boolean {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        cancelPendingCall()

                        viewState = ViewState.Info
                    }
                    return false
                }

                reset()

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
                        cancelPendingCall()

                        viewState = ViewState.VideoDialog.IDLE
                    }
                    return@setOnCallClickListener
                }

                dialog.isInitiator = true

                viewState = ViewState.VideoDialog.Pending

                socketClient?.videoCall()
            }
        }

        audioCallView.setOnCallClickListener {
            val isPermissionRequestSent = checkPermissions()
            if (isPermissionRequestSent) {
                return@setOnCallClickListener
            } else {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        cancelPendingCall()

                        viewState = ViewState.AudioDialog.IDLE
                    }
                    return@setOnCallClickListener
                }

                dialog.isInitiator = true

                viewState = ViewState.AudioDialog.Pending

                socketClient?.audioCall()
            }
        }

        formView.callback = object : FormView.Callback {
            override fun onCancelClicked() {
                viewState = ViewState.ChatBot.UserPrompt
            }

            override fun onSendClicked(name: String, email: String, phone: String) {
                socketClient?.sendFuzzyTaskConfirmation(name, email, phone)

                showFormSentSuccess {
                    formView.clearInputViews()
                    viewState = ViewState.ChatBot.UserPrompt
                }
            }
        }

        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (viewState is ViewState.ChatBot.UserPrompt && bottom < oldBottom) {
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
                if (viewState is ViewState.VideoDialog) {
                    viewState = ViewState.VideoDialog.Live(true)
                } else if (viewState is ViewState.AudioDialog) {
                    viewState = ViewState.AudioDialog.Live(true)
                }
            }

            override fun onAttachmentButtonClicked() {
                val isPermissionRequestSent = checkPermissions()

                if (isPermissionRequestSent) {
                    return
                }

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
            }

            override fun onSendMessageButtonClicked(message: String) {
                if (message.isNotBlank()) {
                    if (viewState is ViewState.ChatBot) {
                        viewState = ViewState.ChatBot.UserPrompt

                        isLoading = true
                    }

                    sendUserMessage(message, true)
                }
            }
        }

        footerView.setOnInputViewFocusChangeListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val text = v?.text.toString()

                if (text.isBlank()) {
                    return@setOnInputViewFocusChangeListener false
                }

                if (viewState is ViewState.ChatBot) {
                    viewState = ViewState.ChatBot.UserPrompt

                    isLoading = true
                }

                sendUserMessage(text, true)

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
                viewState = ViewState.VideoDialog.Live(false)
            }

            override fun onHangupButtonClicked() {
                showHangupConfirmAlert { hangupLiveCall() }
            }

            override fun onSwitchSourceButtonClicked() {
                peerConnectionClient?.onSwitchCamera()
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
                viewState = ViewState.AudioDialog.Live(false)
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

                    socketClient?.setLanguage(selected.key)
                    socketClient?.sendUserLanguage(selected.key)

                    setLanguage(selected.locale)
                }
            }
        }

        peerConnectionClient = PeerConnectionClient()

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

                chatAdapter?.setNewMessages(
                    listOf(
                        Message(
                            type = Message.Type.CROSS_CHILDREN,
                            category = chatBot.activeCategory
                        )
                    )
                )
            }

            override fun onCategoryChildClicked(category: Category) {
                hideKeyboard()

                chatBot.activeCategory = category

                chatRecyclerState = recyclerView.layoutManager?.onSaveInstanceState()

                chatFooterAdapter?.showGoToHomeButton()

                if (category.responses.isNotEmpty()) {
                    socketClient?.getResponse(category.responses.first())
                } else {
                    socketClient?.getCategories(category.id)
                }

                isLoading = true
            }

            override fun onGoBackClicked(category: Category) {
                hideKeyboard()

                val categories = chatBot.allCategories.filter { it.id == category.parentId }

                val messages = if (categories.all { it.parentId == null }) {
                    chatFooterAdapter?.clear()

                    chatBot.basicCategories.map {
                        Message(type = Message.Type.CATEGORY, category = it)
                    }
                } else {
                    categories.map { Message(type = Message.Type.CROSS_CHILDREN, category = it) }
                }

                chatBot.activeCategory = null

                chatAdapter?.setNewMessages(messages)

                chatRecyclerState?.let { chatRecyclerState ->
                    recyclerView.layoutManager?.onRestoreInstanceState(chatRecyclerState)
                }
            }

            override fun onUrlInTextClicked(url: String) {
                if (url.startsWith("#")) {
                    chatFooterAdapter?.showGoToHomeButton()

                    if (viewState is ViewState.ChatBot) {
                        viewState = ViewState.ChatBot.UserPrompt

                        isLoading = true
                    }

                    val text = url.removePrefix("#")
                    sendUserMessage(text, false)
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
                return EdgeEffect(view.context).apply {
                    color = ContextCompat.getColor(
                        this@KenesWidgetV2Activity,
                        R.color.kenes_light_blue
                    )
                }
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
                when (viewState) {
                    is ViewState.VideoDialog -> {
                        chatFooterAdapter?.clear()
                        chatAdapter?.clear()

                        viewState = ViewState.VideoDialog.IDLE
                    }
                    is ViewState.AudioDialog -> {
                        chatFooterAdapter?.clear()
                        chatAdapter?.clear()

                        viewState = ViewState.AudioDialog.IDLE
                    }
                    else -> {
                        val messages = chatBot.basicCategories.map { category ->
                            Message(type = Message.Type.CATEGORY, category = category)
                        }

                        if (messages.isEmpty()) {
                            socketClient?.getBasicCategories()
                        } else {
                            chatAdapter?.setNewMessages(messages)

                            chatRecyclerState?.let { chatRecyclerState ->
                                recyclerView.layoutManager?.onRestoreInstanceState(chatRecyclerState)
                            }
                        }

                        viewState = ViewState.ChatBot.Category
                    }
                }
            }

            override fun onSwitchToCallAgentClicked() {
                if (dialog.isInitiator) {
                    showAlreadyCallingAlert {
                        cancelPendingCall()

                        viewState = ViewState.ChatBot.UserPrompt
                    }
                    return
                }

                dialog.isInitiator = true

                viewState = ViewState.TextDialog.Pending

                chatFooterAdapter?.clear()

                socketClient?.textCall()
            }

            override fun onRegisterAppealClicked() {
                viewState = ViewState.Form
            }
        }

        mergeAdapter = MergeAdapter(chatAdapter, chatFooterAdapter)

        recyclerView.adapter = mergeAdapter

        recyclerView.itemAnimator = null

        recyclerView.addItemDecoration(ChatAdapterItemDecoration(this))
    }

    private fun File.downloadFile(
        position: Int,
        url: String?,
        fileType: String,
        callback: () -> Unit
    ) {
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

    private fun checkPermissions(): Boolean {
        val permissions = permissionsRequest.checkStatus()
        return if (permissions.allGranted()) {
            false
        } else {
            permissionsRequest.send()
            true
        }
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
                debug(TAG, "uploadFile: $path, $hash")

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
                }
            }
        }
    }

    override fun onBackPressed() {
        showWidgetCloseConfirmDialog { finish() }
    }

    override fun finish() {
        closeLiveCall()
        super.finish()
    }

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
            iceServers = data.map {
                PeerConnection.IceServer.builder(it.url)
                    .setUsername(it.username)
                    .setPassword(it.credential)
                    .createIceServer()
            }
        }
    }

    private fun connectToSignallingServer() {
        val signallingServerUrl = UrlUtil.getSignallingServerUrl()
        if (signallingServerUrl.isNullOrBlank()) {
            throw NullPointerException("Signalling server url is null. Please, provide a valid url.")
        } else {
            socketClient = SocketClient()
            socketClient?.start(signallingServerUrl, currentLanguage)
        }

        socketClient?.listener = object : SocketClient.Listener {
            override fun onConnect() {}

            override fun onCallAgentGreet(fullName: String, photoUrl: String?, text: String) {
                if (viewState is ViewState.TextDialog) {
                    viewState = ViewState.TextDialog.Live
                }

                dialog.callAgentName = fullName
                dialog.callAgentAvatarUrl = photoUrl

                val newText = text.replace("{}", fullName)

                runOnUiThread {
                    headerView.setOpponentInfo(
                        Configs.Opponent(
                            name = fullName,
                            secondName = getString(R.string.kenes_call_agent),
                            avatarUrl = photoUrl
                        )
                    )

                    if (viewState is ViewState.AudioDialog) {
                        audioDialogView.setAvatar(photoUrl)
                        audioDialogView.setName(fullName)
                    }

                    chatAdapter?.addNewMessage(
                        Message(type = Message.Type.OPPONENT, text = newText)
                    )
                }
            }

            override fun onFormInit(dynamicForm: DynamicForm) {}

            override fun onFeedback(text: String, ratingButtons: List<RatingButton>) {
                debug(TAG, "onFeedback -> viewState: $viewState")

                runOnUiThread {
                    feedbackView.setTitle(text)
                    feedbackView.setRatingButtons(ratingButtons)
                    feedbackView.setOnRateButtonClickListener { ratingButton ->
                        socketClient?.sendFeedback(ratingButton.rating, ratingButton.chatId)

                        when (viewState) {
                            is ViewState.TextDialog -> {
                                viewState = ViewState.TextDialog.UserFeedback(true)
                                viewState = ViewState.ChatBot.UserPrompt
                            }
                            is ViewState.AudioDialog ->
                                viewState = ViewState.AudioDialog.UserFeedback(true)
                            is ViewState.VideoDialog ->
                                viewState = ViewState.VideoDialog.UserFeedback(true)
                        }
                    }
                }

                when (viewState) {
                    is ViewState.VideoDialog ->
                        viewState = ViewState.VideoDialog.UserFeedback(false)
                    is ViewState.AudioDialog ->
                        viewState = ViewState.AudioDialog.UserFeedback(false)
                    is ViewState.TextDialog ->
                        viewState = ViewState.TextDialog.UserFeedback(false)
                }
            }

            override fun onPendingUsersQueueCount(text: String?, count: Int) {
                runOnUiThread {
                    if (viewState is ViewState.VideoDialog) {
                        if (!text.isNullOrBlank()) {
                            videoCallView.setInfoText(text)
                        }
                        if (count > 1) {
                            videoCallView.setPendingQueueCount(count)
                        }
                    } else if (viewState is ViewState.AudioDialog) {
                        if (!text.isNullOrBlank()) {
                            audioCallView.setInfoText(text)
                        }
                        if (count > 1) {
                            audioCallView.setPendingQueueCount(count)
                        }
                    }
                }
            }

            override fun onNoResultsFound(text: String, timestamp: Long): Boolean {
                debug(TAG, "onNoResultsFound -> viewState: $viewState")

                runOnUiThread {
                    chatAdapter?.addNewMessage(
                        Message(
                            type = Message.Type.OPPONENT,
                            text = text,
                            timestamp = timestamp
                        )
                    )

                    if (viewState is ViewState.ChatBot.UserPrompt) {
                        chatFooterAdapter?.showSwitchToCallAgentButton()
                    }
                }

                isLoading = false

                return true
            }

            override fun onFuzzyTaskOffered(text: String, timestamp: Long): Boolean {
                runOnUiThread {
                    chatAdapter?.addNewMessage(
                        Message(
                            type = Message.Type.OPPONENT,
                            text = text,
                            timestamp = timestamp
                        )
                    )
                    chatFooterAdapter?.showFuzzyQuestionButtons()
                }

                isLoading = false

                return true
            }

            override fun onNoOnlineCallAgents(text: String): Boolean {
                dialog.isInitiator = false

                runOnUiThread {
                    chatAdapter?.addNewMessage(Message(Message.Type.OPPONENT, text))

                    showNoOnlineCallAgents(text) {}

                    when (viewState) {
                        is ViewState.VideoDialog ->
                            viewState = ViewState.VideoDialog.IDLE
                        is ViewState.AudioDialog ->
                            viewState = ViewState.AudioDialog.IDLE
                        is ViewState.TextDialog -> {
                            chatFooterAdapter?.showGoToHomeButton()
                            viewState = ViewState.ChatBot.UserPrompt

                            isLoading = false
                        }
                    }
                }

                return true
            }

            override fun onCallAgentDisconnected(text: String, timestamp: Long): Boolean {
                debug(TAG, "onCallAgentDisconnected -> viewState: $viewState")

                closeLiveCall()

                runOnUiThread {
                    chatAdapter?.addNewMessage(
                        Message(
                            type = Message.Type.NOTIFICATION,
                            text = text,
                            timestamp = timestamp
                        )
                    )
                }

                return true
            }

            override fun onCallAccept() {
                if (viewState is ViewState.VideoDialog) {
                    viewState = ViewState.VideoDialog.Start

                    peerConnectionClient?.createPeerConnection(
                        activity = this@KenesWidgetV2Activity,
                        isMicrophoneEnabled = true,
                        isCameraEnabled = true,
                        iceServers = iceServers,
                        listener = peerConnectionClientListener
                    )

                    peerConnectionClient?.initLocalCameraStream(videoDialogView.localSurfaceView)

                    socketClient?.sendMessage(rtc = rtc { type = RTC.Type.PREPARE })
                } else if (viewState is ViewState.AudioDialog) {
                    viewState = ViewState.AudioDialog.Start

                    peerConnectionClient?.createPeerConnection(
                        activity = this@KenesWidgetV2Activity,
                        isMicrophoneEnabled = true,
                        isCameraEnabled = false,
                        iceServers = iceServers,
                        listener = peerConnectionClientListener
                    )

                    socketClient?.sendMessage(rtc = rtc { type = RTC.Type.PREPARE })
                }
            }

            override fun onRTCPrepare() {
                debug(TAG, "onRTCPrepare: $viewState")

                if (viewState is ViewState.VideoDialog) {
                    viewState = ViewState.VideoDialog.Preparation

                    peerConnectionClient?.addLocalStreamToPeer()

                    socketClient?.sendMessage(rtc = rtc { type = RTC.Type.READY })
                } else if (viewState is ViewState.AudioDialog) {
                    viewState = ViewState.AudioDialog.Preparation

                    peerConnectionClient?.addLocalStreamToPeer()

                    socketClient?.sendMessage(rtc = rtc { type = RTC.Type.READY })
                }
            }

            override fun onRTCReady() {
                debug(TAG, "onRTCReady -> viewState: $viewState")

                if (viewState is ViewState.VideoDialog) {
                    viewState = ViewState.VideoDialog.Ready

                    peerConnectionClient?.addLocalStreamToPeer()
                    peerConnectionClient?.createOffer()
                } else if (viewState is ViewState.AudioDialog) {
                    viewState = ViewState.AudioDialog.Ready

                    peerConnectionClient?.addLocalStreamToPeer()
                    peerConnectionClient?.createOffer()
                }
            }

            override fun onRTCOffer(sessionDescription: SessionDescription) {
                peerConnectionClient?.setRemoteDescription(sessionDescription)
                peerConnectionClient?.createAnswer()
            }

            override fun onRTCAnswer(sessionDescription: SessionDescription) {
                peerConnectionClient?.setRemoteDescription(sessionDescription)
            }

            override fun onRTCIceCandidate(iceCandidate: IceCandidate) {
                peerConnectionClient?.addRemoteIceCandidate(iceCandidate)
            }

            override fun onRTCHangup() {
                closeLiveCall()
            }

            override fun onTextMessage(
                text: String,
                attachments: List<Attachment>?,
                timestamp: Long
            ) {
                debug(TAG, "onTextMessage -> viewState: $viewState")

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

                viewState.let {
                    if (it is ViewState.VideoDialog.Live && it.isDialogScreenShown) {
                        dialog.unreadMessages += 1
                        runOnUiThread {
                            if (dialog.unreadMessages >= Dialog.MAX_UNREAD_MESSAGES_COUNT) {
                                videoDialogView.setUnreadMessagesCount("${dialog.unreadMessages}+")
                            } else {
                                videoDialogView.setUnreadMessagesCount("${dialog.unreadMessages}")
                            }

                            if (videoDialogView.isUnreadMessagesCounterHidden()) {
                                videoDialogView.showUnreadMessagesCounter()
                            }
                        }
                    } else if (it is ViewState.AudioDialog.Live && it.isDialogScreenShown) {
                        dialog.unreadMessages += 1
                        runOnUiThread {
                            if (dialog.unreadMessages >= Dialog.MAX_UNREAD_MESSAGES_COUNT) {
                                audioDialogView.setUnreadMessagesCount("${dialog.unreadMessages}+")
                            } else {
                                audioDialogView.setUnreadMessagesCount("${dialog.unreadMessages}")
                            }

                            if (audioDialogView.isUnreadMessagesCounterHidden()) {
                                audioDialogView.showUnreadMessagesCounter()
                            }
                        }
                    }
                }

                runOnUiThread {
                    chatAdapter?.addNewMessage(
                        Message(
                            type = Message.Type.OPPONENT,
                            text = text,
                            attachments = attachments,
                            timestamp = timestamp
                        )
                    )

                    if (viewState is ViewState.ChatBot) {
                        chatFooterAdapter?.showGoToHomeButton()
                    }
                }

                isLoading = false
            }

            override fun onMediaMessage(media: Media, timestamp: Long) {
                if (media.isImage || media.isFile) {
                    runOnUiThread {
                        chatAdapter?.addNewMessage(
                            Message(
                                type = Message.Type.OPPONENT,
                                media = media,
                                timestamp = timestamp
                            )
                        )
                    }
                }
            }

            override fun onCategories(categories: List<Category>) {
                if (viewState is ViewState.ChatBot.UserPrompt) return

                val sortedCategories = categories.sortedBy { it.id }
                chatBot.allCategories.addAll(sortedCategories)

                if (!chatBot.isBasicCategoriesFilled) {
                    chatBot.allCategories.forEach { category ->
//                        debug(TAG, "category: $category, ${category.parentId == null}")

                        if (category.parentId == null) {
                            socketClient?.getCategories(category.id)
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

            override fun onDisconnect() {
                closeLiveCall()
            }
        }
    }

    private fun cancelPendingCall() {
        socketClient?.cancelPendingCall()

        dialog.isInitiator = false
    }

    private fun hangupLiveCall() {
        dialog.clear()

        runOnUiThread {
            chatAdapter?.addNewMessage(
                Message(
                    type = Message.Type.NOTIFICATION,
                    text = getString(R.string.kenes_user_disconnected)
                )
            )
        }

        peerConnectionClient?.dispose()

        socketClient?.sendMessage(action = UserMessage.Action.FINISH)

        viewState = when (viewState) {
            is ViewState.VideoDialog -> {
                videoDialogView.release()

                ViewState.VideoDialog.UserDisconnected
            }
            is ViewState.AudioDialog -> ViewState.AudioDialog.UserDisconnected
            is ViewState.TextDialog -> ViewState.TextDialog.UserDisconnected
            else -> ViewState.ChatBot.UserPrompt
        }
    }

    private fun closeLiveCall() {
        debug(TAG, "closeLiveCall -> viewState: $viewState")

        dialog.clear()

        videoDialogView.release()

        peerConnectionClient?.dispose()

        when (viewState) {
            is ViewState.TextDialog -> {
                if (viewState !is ViewState.TextDialog.UserFeedback) {
                    viewState = ViewState.TextDialog.CallAgentDisconnected
                }
            }
            is ViewState.AudioDialog -> {
                if (viewState !is ViewState.AudioDialog.UserFeedback) {
                    viewState = ViewState.AudioDialog.CallAgentDisconnected
                }
            }
            is ViewState.VideoDialog -> {
                if (viewState !is ViewState.VideoDialog.UserFeedback) {
                    viewState = ViewState.VideoDialog.CallAgentDisconnected
                }
            }
            else -> {
                viewState = ViewState.ChatBot.UserPrompt
            }
        }
    }

    private fun sendUserMessage(message: String, isInputClearText: Boolean = true) {
        socketClient?.sendUserMessage(message)

        if (isInputClearText) {
            footerView.clearInputViewText()
        }

        chatAdapter?.addNewMessage(Message(type = Message.Type.USER, text = message))
    }

    private val peerConnectionClientListener = object : PeerConnectionClient.Listener {
        override fun onIceCandidate(iceCandidate: IceCandidate) {
            socketClient?.sendMessage(
                rtc = rtc {
                    type = RTC.Type.CANDIDATE
                    id = iceCandidate.sdpMid
                    label = iceCandidate.sdpMLineIndex
                    candidate = iceCandidate.sdp
                }
            )
        }

        override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
            when (iceConnectionState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    if (viewState is ViewState.AudioDialog) {
                        viewState = ViewState.AudioDialog.Live(true)
                    } else if (viewState is ViewState.VideoDialog) {
                        viewState = ViewState.VideoDialog.Live(true)
                    }
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> closeLiveCall()
                else -> {
                }
            }

        }

        override fun onRenegotiationNeeded() {
//            if (dialog.isInitiator) {
//                peerConnectionClient?.createOffer()
//            } else {
//                peerConnectionClient?.createAnswer()
//            }
        }

        override fun onLocalDescription(sessionDescription: SessionDescription) {
            socketClient?.sendMessage(
                rtc = rtc {
                    this.type = RTC.Type.to(sessionDescription.type)
                    this.sdp = sessionDescription.description
                }
            )
        }

        override fun onAddRemoteStream(mediaStream: MediaStream) {
            peerConnectionClient?.initRemoteCameraStream(videoDialogView.remoteSurfaceView)
            peerConnectionClient?.addRemoteStreamToPeer(mediaStream)
        }

        override fun onPeerConnectionError(errorMessage: String) {
        }
    }

    private fun renderViewState(viewState: ViewState) {
        debug(TAG, "renderViewState -> viewState: $viewState")

        when (viewState) {
            is ViewState.ChatBot -> {
                runOnUiThread {
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

                    infoView.visibility = View.GONE

                    bottomNavigationView.setNavButtonsEnabled()
                    bottomNavigationView.setHomeNavButtonActive()

                    recyclerView.visibility = View.VISIBLE

                    footerView.setDefaultState()
                    footerView.visibility = View.VISIBLE
                }

                when (viewState) {
                    ViewState.ChatBot.Category -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                        }
                    }
                    ViewState.ChatBot.UserPrompt -> {
                        runOnUiThread {
                            chatAdapter?.clearCategoryMessages()
                        }

                        chatBot.activeCategory = null
                    }
                }
            }
            is ViewState.TextDialog -> {
                when (viewState) {
                    ViewState.TextDialog.IDLE -> {
                        runOnUiThread {
                            headerView.hideHangupButton()
                            headerView.setOpponentInfo(configs.opponent)

                            feedbackView.setDefaultState()
                            feedbackView.visibility = View.GONE

                            footerView.disableAttachmentButton()

                            bottomNavigationView.setNavButtonsEnabled()
                            bottomNavigationView.setHomeNavButtonActive()
                        }
                    }
                    ViewState.TextDialog.Pending -> {}
                    ViewState.TextDialog.Live -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            headerView.showHangupButton()

                            footerView.enableAttachmentButton()

                            bottomNavigationView.setNavButtonsDisabled()
                        }
                    }
                    ViewState.TextDialog.UserDisconnected, ViewState.TextDialog.CallAgentDisconnected -> {
                        runOnUiThread {
                            headerView.hideHangupButton()

                            footerView.disableAttachmentButton()

                            bottomNavigationView.setNavButtonsEnabled()
                        }
                    }
                    is ViewState.TextDialog.UserFeedback -> {
                        runOnUiThread {
                            if (viewState.isFeedbackSent) {
                                chatFooterAdapter?.showGoToHomeButton()

                                feedbackView.visibility = View.GONE

                                footerView.visibility = View.VISIBLE

                                recyclerView.visibility = View.VISIBLE

                                bottomNavigationView.setNavButtonsEnabled()
                            } else {
                                hideKeyboard(footerView.inputView)

                                recyclerView.visibility = View.GONE

                                footerView.visibility = View.GONE

                                bottomNavigationView.setNavButtonsDisabled()

                                feedbackView.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
            is ViewState.AudioDialog -> {
                runOnUiThread {
                    infoView.visibility = View.GONE
                    videoCallView.visibility = View.GONE
                }

                when (viewState) {
                    ViewState.AudioDialog.IDLE -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            headerView.hideHangupButton()
                            headerView.setOpponentInfo(configs.opponent)

                            audioDialogView.setDefaultState()
                            audioDialogView.visibility = View.GONE

                            feedbackView.setDefaultState()
                            feedbackView.visibility = View.GONE

                            recyclerView.visibility = View.GONE

                            footerView.setGoToActiveDialogButtonState(null)
                            footerView.disableAttachmentButton()
                            footerView.visibility = View.GONE

                            bottomNavigationView.setNavButtonsEnabled()
                            bottomNavigationView.setAudioNavButtonActive()

                            audioCallView.setDefaultState()
                            audioCallView.visibility = View.VISIBLE
                        }
                    }
                    ViewState.AudioDialog.Pending -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            audioCallView.setDisabledState()
                        }
                    }
                    ViewState.AudioDialog.Start -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            hideKeyboard(footerView.inputView)

                            headerView.showHangupButton()

                            audioCallView.setDisabledState()
                            audioCallView.visibility = View.GONE

                            recyclerView.visibility = View.VISIBLE

                            footerView.enableAttachmentButton()
                            footerView.visibility = View.VISIBLE

                            bottomNavigationView.setNavButtonsDisabled()
                        }
                    }
                    ViewState.AudioDialog.Preparation -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                        }
                    }
                    ViewState.AudioDialog.Ready -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                        }
                    }
                    is ViewState.AudioDialog.Live -> {
                        dialog.unreadMessages = 0

                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            footerView.enableAttachmentButton()

                            audioDialogView.setUnreadMessagesCount("0")
                            audioDialogView.hideUnreadMessagesCounter()

                            if (viewState.isDialogScreenShown) {
                                hideKeyboard(footerView.inputView)

                                footerView.setGoToActiveDialogButtonState(null)

                                audioDialogView.visibility = View.VISIBLE
                            } else {
                                footerView.setGoToActiveDialogButtonState(R.string.kenes_return_to_audio_call)

                                audioDialogView.visibility = View.GONE
                            }
                        }
                    }
                    ViewState.AudioDialog.UserDisconnected, ViewState.AudioDialog.CallAgentDisconnected -> {
                        runOnUiThread {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            headerView.hideHangupButton()

                            footerView.setDefaultState()
                            footerView.visibility = View.GONE

                            footerView.disableAttachmentButton()

                            audioDialogView.visibility = View.GONE

                            bottomNavigationView.setNavButtonsEnabled()
                        }
                    }
                    is ViewState.AudioDialog.UserFeedback -> {
                        runOnUiThread {
                            if (viewState.isFeedbackSent) {
                                chatFooterAdapter?.showGoToHomeButton()

                                feedbackView.visibility = View.GONE

                                bottomNavigationView.setNavButtonsEnabled()

                                recyclerView.visibility = View.VISIBLE
                            } else {
                                hideKeyboard(footerView.inputView)

                                audioCallView.visibility = View.GONE

                                recyclerView.visibility = View.GONE

                                bottomNavigationView.setNavButtonsDisabled()

                                feedbackView.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
            is ViewState.VideoDialog -> {
                runOnUiThread {
                    infoView.visibility = View.GONE
                    audioCallView.visibility = View.GONE
                }

                when (viewState) {
                    ViewState.VideoDialog.IDLE -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            headerView.hideHangupButton()
                            headerView.setOpponentInfo(configs.opponent)

                            videoDialogView.setDefaultState()
                            videoDialogView.visibility = View.GONE

                            feedbackView.setDefaultState()
                            feedbackView.visibility = View.GONE

                            recyclerView.visibility = View.GONE

                            footerView.setGoToActiveDialogButtonState(null)
                            footerView.disableAttachmentButton()
                            footerView.visibility = View.GONE

                            bottomNavigationView.setNavButtonsEnabled()
                            bottomNavigationView.setVideoNavButtonActive()

                            videoCallView.setDefaultState()
                            videoCallView.visibility = View.VISIBLE
                        }
                    }
                    ViewState.VideoDialog.Pending -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            videoCallView.setDisabledState()
                        }
                    }
                    ViewState.VideoDialog.Start -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            hideKeyboard(footerView.inputView)

                            headerView.showHangupButton()

                            videoCallView.setDisabledState()
                            videoCallView.visibility = View.GONE

                            recyclerView.visibility = View.VISIBLE

                            footerView.enableAttachmentButton()
                            footerView.visibility = View.VISIBLE

                            bottomNavigationView.setNavButtonsDisabled()
                        }
                    }
                    ViewState.VideoDialog.Preparation -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                        }
                    }
                    ViewState.VideoDialog.Ready -> {
                        runOnUiThread {
                            chatFooterAdapter?.clear()
                        }
                    }
                    is ViewState.VideoDialog.Live -> {
                        dialog.unreadMessages = 0

                        runOnUiThread {
                            chatFooterAdapter?.clear()

                            footerView.enableAttachmentButton()

                            videoDialogView.setUnreadMessagesCount("0")
                            videoDialogView.hideUnreadMessagesCounter()

                            if (viewState.isDialogScreenShown) {
                                hideKeyboard(footerView.inputView)

                                footerView.setGoToActiveDialogButtonState(null)

                                videoDialogView.showControlButtons()
                                videoDialogView.visibility = View.VISIBLE
                            } else {
                                footerView.setGoToActiveDialogButtonState(R.string.kenes_return_to_video_call)

                                videoDialogView.visibility = View.GONE
                            }
                        }
                    }
                    ViewState.VideoDialog.UserDisconnected, ViewState.VideoDialog.CallAgentDisconnected -> {
                        runOnUiThread {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                            headerView.hideHangupButton()

                            footerView.setDefaultState()
                            footerView.visibility = View.GONE

                            footerView.disableAttachmentButton()

                            videoDialogView.visibility = View.GONE

                            bottomNavigationView.setNavButtonsEnabled()
                        }
                    }
                    is ViewState.VideoDialog.UserFeedback -> {
                        runOnUiThread {
                            if (viewState.isFeedbackSent) {
                                chatFooterAdapter?.showGoToHomeButton()

                                feedbackView.visibility = View.GONE

                                recyclerView.visibility = View.VISIBLE

                                bottomNavigationView.setNavButtonsEnabled()
                            } else {
                                hideKeyboard(footerView.inputView)

                                videoCallView.visibility = View.GONE

                                recyclerView.visibility = View.GONE

                                bottomNavigationView.setNavButtonsDisabled()

                                feedbackView.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
            ViewState.Form -> {
                recyclerView.visibility = View.GONE

                footerView.visibility = View.GONE

                bottomNavigationView.setNavButtonsDisabled()

                formView.visibility = View.VISIBLE
            }
            ViewState.Info -> {
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

    override fun onDestroy() {
        super.onDestroy()

        footerView.disableAttachmentButton()

        isLoading = false

        viewState = ViewState.ChatBot.Category

        closeLiveCall()

        iceServers = emptyList()

        configs.clear()
        chatBot.clear()
        dialog.clear()

        socketClient?.release()
        socketClient?.listener = null
        socketClient = null

        peerConnectionClient = null

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
//            e.printStackTrace()
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
        Log.e(TAG, getString(R.string.kenes_error_invalid_hostname))
        Toast.makeText(this, R.string.kenes_error_invalid_hostname, Toast.LENGTH_SHORT)
            .show()
        finish()
    }

}