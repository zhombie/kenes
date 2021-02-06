package q19.kenes.widget.ui.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kz.q19.common.error.ViewHolderViewTypeException
import kz.q19.domain.model.call.CallType
import kz.q19.domain.model.configs.Configs
import kz.q19.domain.model.language.Language
import kz.q19.utils.textview.removeCompoundDrawables
import kz.q19.utils.textview.showCompoundDrawableOnLeft
import q19.kenes.widget.ui.components.base.TextView
import q19.kenes.widget.ui.components.base.TitleView
import q19.kenes.widget.ui.util.*
import q19.kenes.widget.util.Logger.debug
import q19.kenes.widget.util.inflate
import q19.kenes_widget.R

internal class CallTypeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    companion object {
        private val TAG = CallTypeView::class.java.simpleName
    }

    private var contentView: LinearLayout? = null

    private var audioCallButton: LinearLayout? = null
    private var videoCallButton: LinearLayout? = null

    private var titleView: TitleView? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: CallsAdapter? = null

    var callback: Callback? = null

    init {
        orientation = VERTICAL
    }

    private fun buildContentView() {
        val scrollView = ScrollView(context)
        scrollView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        contentView = LinearLayout(context)
        contentView?.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        contentView?.setPadding(
            resources.getDimensionPixelOffset(R.dimen.kenes_horizontal_spacing),
            0,
            resources.getDimensionPixelOffset(R.dimen.kenes_horizontal_spacing),
            0
        )
        contentView?.orientation = VERTICAL

        scrollView.addView(contentView)

        addView(scrollView)
    }

    private fun buildButtonView(
        @DrawableRes background: Int,
        @DrawableRes icon: Int,
        titleHexColor: String,
        @StringRes titleText: Int,
        definitionHexColor: String,
        @StringRes definitionText: Int
    ): LinearLayout {
        // Parent view
        val callButton = LinearLayout(context)
        callButton.setPadding(
            context.resources.getDimensionPixelOffset(R.dimen.kenes_call_button_padding),
            context.resources.getDimensionPixelOffset(R.dimen.kenes_call_button_padding),
            context.resources.getDimensionPixelOffset(R.dimen.kenes_call_button_padding),
            context.resources.getDimensionPixelOffset(R.dimen.kenes_call_button_padding)
        )
        callButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        callButton.orientation = VERTICAL
        callButton.background = ResourcesCompat.getDrawable(
            resources,
            background,
            context.theme
        )
        callButton.isClickable = true
        callButton.isFocusable = true

        // Icon
        val callIconView = ImageView(context)
        val callIconViewLayoutParams = MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            context.resources.getDimensionPixelOffset(R.dimen.kenes_call_icon_height)
        )
        callIconViewLayoutParams.bottomMargin = 15
        callIconView.layoutParams = callIconViewLayoutParams
        callIconView.adjustViewBounds = true
        callIconView.setImageResource(icon)

        callButton.addView(callIconView)

        // Title
        val titleView = TextView(context)
        titleView.layoutParams =
            MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        titleView.setTextAppearance(R.style.BoldTitle)
        titleView.setText(titleText)
        titleView.setTextColor(Color.parseColor(titleHexColor))

        callButton.addView(titleView)

        // Definition
        val definitionView = TextView(context)
        val definitionViewLayoutParams =
            MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        definitionViewLayoutParams.topMargin = 3
        definitionView.layoutParams = definitionViewLayoutParams
        definitionView.setTextAppearance(R.style.StandardSubtitle)
        definitionView.setText(definitionText)
        definitionView.setTextColor(Color.parseColor(definitionHexColor))

        callButton.addView(definitionView)

        return callButton
    }

    fun showCallButton(callType: CallType) {
        setCallButtonVisibility(callType, true)
    }

    fun hideCallButton(callType: CallType) {
        setCallButtonVisibility(callType, false)
    }

    private fun setCallButtonVisibility(callType: CallType, isVisible: Boolean) {
        if ((callType == CallType.AUDIO || callType == CallType.VIDEO) &&
            contentView == null
        ) {
            buildContentView()
        }

        if (callType == CallType.AUDIO) {
            if (isVisible) {
                if (!contentView.isViewAdded(audioCallButton)) {
                    audioCallButton = buildButtonView(
                        background = R.drawable.kenes_bg_orange,
                        icon = R.drawable.kenes_ic_man_raising_hand,
                        titleHexColor = "#FFA000",
                        titleText = R.string.kenes_audio_call,
                        definitionHexColor = "#95FFA000",
                        definitionText = R.string.kenes_audio_call_to_operator
                    )

                    audioCallButton?.setOnClickListener {
                        callback?.onCallTypeClicked(callType)
                    }

                    contentView?.addView(audioCallButton)
                }
            } else {
                if (contentView.isViewAdded(audioCallButton)) {
                    contentView?.removeView(audioCallButton)
                }
            }
        } else if (callType == CallType.VIDEO) {
            if (isVisible) {
                if (!contentView.isViewAdded(videoCallButton)) {
                    videoCallButton = buildButtonView(
                        background = R.drawable.kenes_bg_green,
                        icon = R.drawable.kenes_ic_female_technologist,
                        titleHexColor = "#4BB34B",
                        titleText = R.string.kenes_video_call,
                        definitionHexColor = "#954BB34B",
                        definitionText = R.string.kenes_video_call_to_operator
                    )

                    videoCallButton?.setOnClickListener {
                        callback?.onCallTypeClicked(callType)
                    }

                    if (contentView.isViewAdded(audioCallButton)) {
                        (videoCallButton?.layoutParams as? MarginLayoutParams)?.topMargin = 15
                    }

                    contentView?.addView(videoCallButton)
                }
            } else {
                if (contentView.isViewAdded(videoCallButton)) {
                    contentView?.removeView(videoCallButton)
                }
            }
        }
    }

    private fun ViewGroup?.isViewAdded(view: View?): Boolean {
        if (this == null) return false
        return view != null && this.indexOfChild(view) != -1
    }

    fun setCallButtonEnabled(callType: CallType) {
        setCallButtonEnabled(callType, true)
    }

    fun setCallButtonDisabled(callType: CallType) {
        setCallButtonEnabled(callType, false)
    }

    private fun setCallButtonEnabled(callType: CallType, isEnabled: Boolean) {
        if (callType == CallType.AUDIO) {
            if (audioCallButton?.isEnabled == isEnabled) return
            audioCallButton?.isEnabled = isEnabled
        } else if (callType == CallType.VIDEO) {
            if (videoCallButton?.isEnabled == isEnabled) return
            videoCallButton?.isEnabled = isEnabled
        }
    }

    fun showCalls(parentCall: Configs.Call?, calls: List<Configs.Call>, language: Language) {
        if (titleView == null) {
            titleView = TitleView(context)
            titleView?.layoutParams = MarginLayoutParams(
                MarginLayoutParams.MATCH_PARENT,
                MarginLayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins(
                    0,
                    0,
                    0,
                    context.resources.getDimensionPixelOffset(R.dimen.kenes_title_bottom_offset)
                )
            }
            titleView?.setPadding(
                resources.getDimensionPixelOffset(R.dimen.kenes_horizontal_spacing),
                resources.getDimensionPixelOffset(R.dimen.kenes_title_padding),
                resources.getDimensionPixelOffset(R.dimen.kenes_horizontal_spacing),
                resources.getDimensionPixelOffset(R.dimen.kenes_title_padding)
            )
        }

        if (parentCall == null) {
            titleView?.hideBackButtonOnLeft()

            titleView?.setText(R.string.kenes_call_with_operator)

            titleView?.isClickable = false

            titleView?.removeBackground()

            titleView?.removeClickListeners()

            adapter?.isFooterEnabled = false
        } else {
            titleView?.showBackButtonOnLeft()

            titleView?.text = parentCall.title.get(language)

            titleView?.isClickable = true

            titleView?.setRippleBackground()

            if (titleView?.hasOnClickListeners() == false) {
                titleView?.setOnClickListener { callback?.onCallBackClicked() }
            }

            adapter?.isFooterEnabled = true
        }

        if (!isViewAdded(titleView)) {
            addView(titleView)
        }

        if (recyclerView == null) {
            recyclerView = RecyclerView(context)
            val layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )

            layoutParams.setMargins(
                resources.getDimensionPixelOffset(R.dimen.kenes_horizontal_spacing),
                0,
                resources.getDimensionPixelOffset(R.dimen.kenes_horizontal_spacing),
                0
            )

            recyclerView?.setPadding(
                0,
                0,
                0,
                context.resources.getDimensionPixelOffset(R.dimen.kenes_section_padding)
            )

            recyclerView?.clipToPadding = false

            recyclerView?.layoutParams = layoutParams

            recyclerView?.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }

        if (!isViewAdded(recyclerView)) {
            addView(recyclerView)
        }

        if (adapter == null) {
            adapter = CallsAdapter(language, object : CallsAdapter.Callback {
                override fun onCallClicked(call: Configs.Call) {
                    callback?.onCallClicked(call)
                }

                override fun onCallBackClicked() {
                    callback?.onCallBackClicked()
                }
            })

            recyclerView?.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            recyclerView?.adapter = adapter

            recyclerView?.addItemDecoration(
                CallsAdapterItemDecoration(
                context,
                resources.getDimension(R.dimen.kenes_rounded_border_width),
                resources.getDimension(R.dimen.kenes_rounded_border_radius)
            )
            )
        }

        adapter?.calls = calls
    }

    fun removeListener(callType: CallType) {
        if (callType == CallType.AUDIO) {
            audioCallButton?.setOnClickListener(null)
        } else if (callType == CallType.VIDEO) {
            videoCallButton?.setOnClickListener(null)
        }
    }

    interface Callback {
        fun onCallClicked(call: Configs.Call)
        fun onCallBackClicked()

        fun onCallTypeClicked(callType: CallType)
    }

}


private class CallsAdapter constructor(
    private val language: Language,
    private val callback: Callback
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private val TAG = CallsAdapter::class.java.simpleName

        private val LAYOUT_HORIZONTAL_BUTTON = R.layout.kenes_cell_horizontal_button

        const val VIEW_TYPE_CALL_SCOPE = 100
        const val VIEW_TYPE_FOOTER = 101
    }

    var calls: List<Configs.Call> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var isFooterEnabled: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int {
        return if (isFooterEnabled) {
            if (position == itemCount - 1) {
                VIEW_TYPE_FOOTER
            } else {
                VIEW_TYPE_CALL_SCOPE
            }
        } else {
            VIEW_TYPE_CALL_SCOPE
        }
    }

    override fun getItemCount(): Int = calls.size + if (isFooterEnabled) 1 else 0

    fun getItem(position: Int): Configs.Call? {
        if (position < 0) {
            return null
        }
        return calls[position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CALL_SCOPE -> CallsViewHolder(parent.inflate(LAYOUT_HORIZONTAL_BUTTON))
            VIEW_TYPE_FOOTER -> FooterViewHolder(parent.inflate(LAYOUT_HORIZONTAL_BUTTON))
            else -> throw ViewHolderViewTypeException(viewType)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is CallsViewHolder) {
            val item = getItem(position)
            item?.let { holder.bind(it) }
        } else if (holder is FooterViewHolder) {
            holder.bind()
        }
    }

    private inner class CallsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<AppCompatTextView>(R.id.textView)
        private val imageView = view.findViewById<AppCompatImageView>(R.id.imageView)

        fun bind(call: Configs.Call) {
            textView?.text = call.title.get(language)

            debug(TAG, "call: $call")

            when (call.type) {
                Configs.Nestable.Type.FOLDER -> {
                    imageView?.setImageResource(R.drawable.kenes_ic_caret_right_blue)
                    imageView?.visibility = View.VISIBLE

                    textView?.removeCompoundDrawables()

                    itemView.isClickable = true
                    itemView.isFocusable = true

                    itemView.background = buildRippleDrawable(itemView.context)

                    itemView.setOnClickListener { callback.onCallClicked(call) }
                }
                Configs.Nestable.Type.LINK -> {
                    imageView?.visibility = View.GONE

                    when (call.callType) {
                        CallType.AUDIO -> {
                            val drawable = setDrawableTint(
                                itemView.context,
                                R.drawable.kenes_ic_headphones_blue,
                                ContextCompat.getColor(itemView.context, R.color.kenes_bright_blue)
                            )
                            textView?.showCompoundDrawableOnLeft(
                                drawable,
                                itemView.context.resources.getDimensionPixelOffset(R.dimen.kenes_title_compound_drawable_padding)
                            )
                        }
                        CallType.VIDEO -> {
                            val drawable = setDrawableTint(
                                itemView.context,
                                R.drawable.kenes_ic_camera_blue,
                                ContextCompat.getColor(itemView.context, R.color.kenes_bright_blue)
                            )
                            textView?.showCompoundDrawableOnLeft(
                                drawable,
                                itemView.context.resources.getDimensionPixelOffset(R.dimen.kenes_title_compound_drawable_padding)
                            )
                        }
                        else -> {
                            textView.removeCompoundDrawables()
                        }
                    }

                    itemView.isClickable = true
                    itemView.isFocusable = true

                    itemView.background = buildRippleDrawable(itemView.context)

                    itemView.setOnClickListener { callback.onCallClicked(call) }
                }
                else -> {
                    imageView?.visibility = View.GONE

                    textView?.removeCompoundDrawables()

                    itemView.isClickable = false
                    itemView.isFocusable = false

                    itemView.background = buildSimpleDrawable(itemView.context)

                    itemView.setOnClickListener(null)
                }
            }
        }
    }

    private inner class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<AppCompatTextView>(R.id.textView)

        fun bind() {
            textView?.removeCompoundDrawables()

            textView?.setText(R.string.kenes_back)

            itemView.isClickable = true
            itemView.isFocusable = true

            itemView.background = buildRippleDrawable(itemView.context)

            itemView.setOnClickListener { callback.onCallBackClicked() }
        }
    }

    interface Callback {
        fun onCallClicked(call: Configs.Call)
        fun onCallBackClicked()
    }

}


private class CallsAdapterItemDecoration(
    context: Context,
    strokeWidth: Float,
    private val cornerRadius: Float
) : RecyclerView.ItemDecoration() {

    private val paint: Paint = Paint()

    init {
        paint.color = ContextCompat.getColor(context, R.color.kenes_very_light_grayish_blue)
        paint.strokeWidth = strokeWidth
        paint.style = Paint.Style.STROKE
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = (parent.adapter as? CallsAdapter?) ?: return

        val itemCount = parent.childCount
//        val itemCount = adapter.itemCount

        if (parent.childCount == 1) {
            val child = parent.getChildAt(0)
            c.drawRoundRect(
                child.left.toFloat(),
                child.top.toFloat(),
                child.right.toFloat(),
                child.bottom.toFloat(),
                parent.context.resources.getDimension(R.dimen.kenes_rounded_border_radius),
                parent.context.resources.getDimension(R.dimen.kenes_rounded_border_radius),
                paint
            )
        } else {
            for (index in 0 until itemCount) {
                val child = parent.getChildAt(index)

                val layoutParams = child.layoutParams as RecyclerView.LayoutParams

                val viewType = adapter.getItemViewType(index)

                if (viewType == CallsAdapter.VIEW_TYPE_FOOTER) {
                    val path = getPathOfRoundedRectF(
                        child,
                        radius = parent.context.resources.getDimension(R.dimen.kenes_rounded_border_radius)
                    )
                    c.drawPath(path, paint)
                } else if (viewType == CallsAdapter.VIEW_TYPE_CALL_SCOPE) {
                    val relationalItemCount = if (adapter.isFooterEnabled) {
                        itemCount - 1
                    } else {
                        itemCount
                    }

                    // Divider
                    if (itemCount > 3 && index < relationalItemCount - 1) {
                        val startX = parent.paddingStart
                        val stopX = parent.width
                        val y = child.bottom + layoutParams.bottomMargin
                        c.drawLine(
                            startX.toFloat(),
                            y.toFloat(),
                            stopX.toFloat(),
                            y.toFloat(),
                            paint
                        )
                    }

                    if (relationalItemCount == 1) {
                        val path = getPathOfRoundedRectF(
                            child,
                            radius = parent.context.resources.getDimension(R.dimen.kenes_rounded_border_radius)
                        )
                        c.drawPath(path, paint)
                    } else {
                        when (index) {
                            0 -> {
                                val path = getPathOfQuadTopRectF(
                                    child,
                                    radius = parent.context.resources.getDimension(R.dimen.kenes_rounded_border_radius)
                                )
                                c.drawPath(path, paint)
                            }
                            relationalItemCount - 1 -> {
                                val path = getPathOfQuadBottomRectF(
                                    child,
                                    radius = parent.context.resources.getDimension(R.dimen.kenes_rounded_border_radius)
                                )
                                c.drawPath(path, paint)
                            }
                            else -> {
                                val path = Path()
                                path.moveTo(child.left.toFloat(), child.top.toFloat())
                                path.lineTo(child.left.toFloat(), child.bottom.toFloat())
                                path.moveTo(child.right.toFloat(), child.top.toFloat())
                                path.lineTo(child.right.toFloat(), child.bottom.toFloat())
                                path.close()
                                c.drawPath(path, paint)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        val adapter = (parent.adapter as? CallsAdapter?) ?: return

//        val itemCount = parent.childCount
        val itemCount = adapter.itemCount

        val position = parent.layoutManager?.getPosition(view) ?: -1

        val viewType = adapter.getItemViewType(position)

        // Offset between elements
        when (viewType) {
            CallsAdapter.VIEW_TYPE_CALL_SCOPE ->
                outRect.setEmpty()
            CallsAdapter.VIEW_TYPE_FOOTER ->
                outRect.top = parent.context.resources.getDimensionPixelOffset(R.dimen.kenes_footer_vertical_offset)
            else ->
                outRect.setEmpty()
        }

        // Draw rounded background
        if (cornerRadius.compareTo(0f) != 0) {
            val roundMode = if (viewType == CallsAdapter.VIEW_TYPE_FOOTER) {
                RoundMode.ALL
            } else if (viewType == CallsAdapter.VIEW_TYPE_CALL_SCOPE) {
                val relationalItemCount = if (adapter.isFooterEnabled) {
                    itemCount - 1
                } else {
                    itemCount
                }

                if (relationalItemCount == 1) {
                    RoundMode.ALL
                } else {
                    when (parent.getChildAdapterPosition(view)) {
                        0 -> RoundMode.TOP
                        relationalItemCount - 1 -> RoundMode.BOTTOM
                        else -> RoundMode.NONE
                    }
                }
            } else {
                RoundMode.NONE
            }

            val outlineProvider = view.outlineProvider
            if (outlineProvider is RoundOutlineProvider) {
                outlineProvider.roundMode = roundMode
                view.invalidateOutline()
            } else {
                view.outlineProvider = RoundOutlineProvider(cornerRadius, roundMode)
                view.clipToOutline = true
            }
        }
    }

}