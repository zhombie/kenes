package q19.kenes_widget.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import q19.kenes_widget.R
import q19.kenes_widget.model.RatingButton

internal class FeedbackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val titleView: TextView
    private val ratingView: RecyclerView
    private val rateButton: AppCompatButton

    private var selectedRatingButton: RatingButton? = null

    init {
        val view = inflate(context, R.layout.kenes_view_feedback, this)

        titleView = view.findViewById(R.id.titleView)
        ratingView = view.findViewById(R.id.ratingView)
        rateButton = view.findViewById(R.id.rateButton)

        ratingView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    }

    fun setDefaultState() {
        rateButton.isEnabled = false
        titleView.text = null
        ratingView.adapter = null
    }

    fun setTitle(title: String) {
        titleView.text = title
    }

    fun setRatingButtons(ratingButtons: List<RatingButton>) {
        val ratingAdapter = RatingAdapter(ratingButtons) {
            selectedRatingButton = it

            if (selectedRatingButton != null) {
                rateButton.isEnabled = true
            }
        }

        ratingView.adapter = ratingAdapter

        ratingAdapter.notifyDataSetChanged()
    }

    fun setOnRateButtonClickListener(callback: (ratingButton: RatingButton) -> Unit) {
        rateButton.setOnClickListener {
            callback(selectedRatingButton ?: return@setOnClickListener)

            setDefaultState()

            selectedRatingButton = null
        }
    }

}

private class RatingAdapter(
    private val ratingButtons: List<RatingButton>,
    private val callback: (ratingButton: RatingButton) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        val LAYOUT_RATING = R.layout.kenes_cell_rating
    }

    init {
        setHasStableIds(true)
    }

    private var selectedRatingButtonPosition: Int = -1

    fun getSelectedRatingButton(): RatingButton? {
        return if (selectedRatingButtonPosition > -1 && selectedRatingButtonPosition < ratingButtons.size) {
            ratingButtons[selectedRatingButtonPosition]
        } else {
            null
        }
    }

    override fun getItemId(position: Int): Long {
        val ratingButton = ratingButtons[position]
        return ratingButton.chatId + ratingButton.rating
    }

    override fun getItemCount(): Int = ratingButtons.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(LAYOUT_RATING, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolder) {
            holder.bind(ratingButtons[position], callback)
        }
    }

    private inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private var textView: TextView? = null

        init {
            textView = view.findViewById(R.id.textView)
        }

        fun bind(
            ratingButton: RatingButton,
            callback: (ratingButton: RatingButton) -> Unit
        ) {
            textView?.isActivated = selectedRatingButtonPosition == adapterPosition

            textView?.text = ratingButton.title

            textView?.setOnClickListener {
                val tempPosition = selectedRatingButtonPosition

                selectedRatingButtonPosition = adapterPosition
                notifyItemChanged(tempPosition)
                notifyItemChanged(selectedRatingButtonPosition)
                callback(ratingButton)
            }
        }
    }

}