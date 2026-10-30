package com.vestigiascriptorium.android.genealogy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** Explicit provider chooser, including cloud apps that implement GET_CONTENT. */
internal class CloudImagePicker: ActivityResultContract<Unit,Uri?>() {
    override fun createIntent(context: Context,input: Unit): Intent=Intent.createChooser(
        Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_LOCAL_ONLY,false),null)
    override fun parseResult(resultCode: Int,intent: Intent?): Uri?=if(resultCode==Activity.RESULT_OK)intent?.data else null
}
