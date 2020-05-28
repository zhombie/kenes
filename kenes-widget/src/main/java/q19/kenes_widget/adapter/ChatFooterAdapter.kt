package q19.kenes_widget.adapter

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import q19.kenes_widget.R
import q19.kenes_widget.core.errors.ViewHolderViewTypeException
import q19.kenes_widget.model.Footer
import q19.kenes_widget.util.inflate
import q19.kenes_widget.util.removeCompoundDrawables
import q19.kenes_widget.util.showCompoundDrawableOnfLeft

class ChatFooterAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private val LAYOUT_FOOTER = R.layout.kenes_cell_footer
        private val LAYOUT_FOOTER_FUZZY = R.layout.kenes_cell_footer_fuzzy
    }

    private var data = mutableListOf<Footer>()

    var callback: Callback? = null

    fun showGoToHomeButton() {
        showButton(Footer.Type.GO_TO_HOME)
    }

    fun showSwitchToCallAgentButton() {
        showButton(Footer.Type.SWITCH_TO_CALL_AGENT)
    }

    fun showFuzzyQuestionButtons() {
        showButton(Footer.Type.FUZZY_QUESTION)
    }

    private fun showButton(type: Footer.Type) {
        if (data.isEmpty()) {
            data.add(0, Footer(type))
            notifyItemInserted(0)
        } else {
            if (data[0].type == type) {
                return
            } else {
                data[0] = Footer(type)
                notifyItemChanged(0)
            }
        }
    }

    fun clear() {
        if (data.isEmpty()) return

        data.clear()
        notifyItemRemoved(0)
    }

    override fun getItemViewType(position: Int): Int {
        return if (data.isNotEmpty()) {
            when (data[position].type) {
                Footer.Type.GO_TO_HOME -> LAYOUT_FOOTER
                Footer.Type.SWITCH_TO_CALL_AGENT -> LAYOUT_FOOTER
                Footer.Type.FUZZY_QUESTION -> LAYOUT_FOOTER_FUZZY
            }
        } else {
            super.getItemViewType(position)
        }
    }

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = parent.inflate(viewType)
        return when (viewType) {
            LAYOUT_FOOTER -> FooterViewHolder(view)
            LAYOUT_FOOTER_FUZZY -> FuzzyFooterViewHolder(view)
            else -> throw ViewHolderViewTypeException(viewType)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterViewHolder) {
            holder.bind(data[position])
        } else if (holder is FuzzyFooterViewHolder) {
            holder.bind(data[position])
        }
    }

    private inner class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val button = view.findViewById<AppCompatButton>(R.id.button)

        fun bind(footer: Footer) {
            if (footer.type == Footer.Type.GO_TO_HOME) {
                button.showCompoundDrawableOnfLeft(R.drawable.kenes_selector_arrow_left, 15)
                button.setText(R.string.kenes_go_to_home)
                button.setOnClickListener { callback?.onGoToHomeClicked() }
            } else if (footer.type == Footer.Type.SWITCH_TO_CALL_AGENT) {
                button.removeCompoundDrawables()
                button.setText(R.string.kenes_switch_to_call_agent)
                button.setOnClickListener { callback?.onSwitchToCallAgentClicked() }
            }
        }
    }

    private inner class FuzzyFooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val button1 = view.findViewById<AppCompatButton>(R.id.button1)
        private val button2 = view.findViewById<AppCompatButton>(R.id.button2)

        fun bind(footer: Footer) {
            if (footer.type == Footer.Type.FUZZY_QUESTION) {
                button1.showCompoundDrawableOnfLeft(R.drawable.kenes_selector_headphones, 15)
                button1.setText(R.string.kenes_switch_to_call_agent)
                button1.setOnClickListener { callback?.onSwitchToCallAgentClicked() }

                button2.removeCompoundDrawables()
                button2.setText(R.string.kenes_register_appeal)
                button2.setOnClickListener { callback?.onRegisterAppealClicked() }
            }
        }
    }

    interface Callback {
        fun onGoToHomeClicked()
        fun onSwitchToCallAgentClicked()
        fun onRegisterAppealClicked()
    }

}