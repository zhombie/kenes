package q19.kenes_widget.ui.components.base

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.StyleRes
import androidx.appcompat.widget.AppCompatTextView

open class TextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    override fun setTextAppearance(@StyleRes resId: Int) {
        if (Build.VERSION.SDK_INT >= 23) {
            super.setTextAppearance(resId)
        } else {
            @Suppress("DEPRECATION")
            super.setTextAppearance(context, resId)
        }
    }

}