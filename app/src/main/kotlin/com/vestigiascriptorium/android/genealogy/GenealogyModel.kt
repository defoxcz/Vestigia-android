package com.vestigiascriptorium.android.genealogy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal data class GenealogyState(val revision: Long=0,val ready: Boolean=false,val busy: Boolean=false,val trees: List<Pair<String,String>> = emptyList(),val tree: FamilyTree?=null,val view: GraphView=GraphView(),val undo: Boolean=false,val redo: Boolean=false,val error: String?=null)
internal class GenealogyController(private val store: GenealogyStore,private val scope: CoroutineScope) {
    private val mutable=MutableStateFlow(GenealogyState())
    val state=mutable.asStateFlow()
    private val lock=Mutex()
    private val past=ArrayDeque<FamilyTree>();private val future=ArrayDeque<FamilyTree>()
    private var viewJob: Job?=null
    init {run { val trees=store.list();val id=store.last();val tree=id?.takeIf {key -> trees.any {it.first==key} }?.let(store::read)
        mutable.value=GenealogyState(ready=true,trees=trees,tree=tree,view=tree?.let {store.view(it.id)} ?: GraphView()) } }
    private fun run(action: suspend ()->Unit) {scope.launch {lock.withLock {
        mutable.update {it.copy(busy=true,error=null)}
        try {withContext(Dispatchers.IO) {action()}}
        catch(e: CancellationException) {throw e}
        catch(e: Exception) {mutable.update {it.copy(error=(e as? GenealogyProblem)?.code ?: "storage")}}
        finally {mutable.update {it.copy(ready=true,busy=false,undo=past.isNotEmpty(),redo=future.isNotEmpty())}}
    }}}
    fun clearError() {mutable.update {it.copy(error=null)}}
    fun create(title: String) = run {
        val tree=FamilyTree(title=title.trim());store.save(tree);store.last(tree.id)
        val list=store.list();past.clear();future.clear();mutable.update {it.copy(trees=list,tree=tree,view=GraphView())}
    }
    fun open(id: String)=run {val tree=store.read(id);store.last(id);val view=store.view(id);past.clear();future.clear();mutable.update {it.copy(tree=tree,view=view)}}
    fun closeTree()=run {mutable.value.tree?.let {store.view(it.id,mutable.value.view)};store.last(null);val list=store.list();past.clear();future.clear();mutable.update {it.copy(tree=null,trees=list)}}
    fun edit(action: (FamilyTree)->FamilyTree)=run {
        val before=mutable.value.tree ?: return@run
        val after=action(before);if(after==before) {mutable.update {it.copy(revision=it.revision+1)};return@run}
        store.save(after) // Publish/record history only after the transaction succeeds.
        past.addLast(before);while(past.size>40)past.removeFirst();future.clear()
        val list=store.list();mutable.update {it.copy(revision=it.revision+1,tree=after,trees=list)}
    }
    fun undo()=history(past,future)
    fun redo()=history(future,past)
    private fun history(from: ArrayDeque<FamilyTree>,to: ArrayDeque<FamilyTree>)=run {
        val before=mutable.value.tree ?: return@run;val after=from.lastOrNull() ?: return@run
        store.save(after);from.removeLast();to.addLast(before);mutable.update {it.copy(revision=it.revision+1,tree=after)}
    }
    fun view(value: GraphView) {
        mutable.update {it.copy(view=value)}
        val id=mutable.value.tree?.id ?: return
        viewJob?.cancel();viewJob=scope.launch {delay(250);lock.withLock {withContext(Dispatchers.IO) {try {store.view(id,value)} catch(e: Exception) {if(e is CancellationException)throw e;mutable.update {it.copy(error="storage")}}}}}
    }
}
internal class GenealogyModel(app: Application): AndroidViewModel(app) {
    // Opening and schema creation happen in the controller's IO dispatcher as well.
    private var store: GenealogyStore?=null
    val controller=MutableStateFlow<GenealogyController?>(null)
    val failed=MutableStateFlow(false)
    init {viewModelScope.launch {try {
        val opened=withContext(Dispatchers.IO) {GenealogyStore(File(app.noBackupFilesDir,"genealogy"))};store=opened
        controller.value=GenealogyController(opened,viewModelScope)
    } catch(e: Exception) {if(e is CancellationException)throw e;failed.value=true}}}
    override fun onCleared() {store?.close()}
}
