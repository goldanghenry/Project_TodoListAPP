package com.henry_ub.todolist

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.IdpResponse
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.henry_ub.todolist.databinding.ActivityMainBinding
import com.henry_ub.todolist.databinding.ItemTodoBinding

class MainActivity : AppCompatActivity() {
    val RC_SIGN_IN = 1000
    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // 로그인이 안 됨
        // currentUser는 현재 인증 정보를 나타낸다.
        if (FirebaseAuth.getInstance().currentUser == null) {
            login()
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = TodoAdapter(
                emptyList(),
                onClickDeleteIcon = {
                    viewModel.deleteTodo(it)
                },
                onClickItem = {
                    viewModel.toggleTodo(it)
                }
            )
        }
        binding.addButton.setOnClickListener {
            val todo = Todo(binding.editText.text.toString())
            viewModel.addTodo(todo)
        }

        // 관찰 UI 업데이트
        viewModel.todoLiveData.observe(this, Observer {
            (binding.recyclerView.adapter as TodoAdapter).setData(it)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) { //OK는 sign in이 된 상태
                // Successfully signed in
                viewModel.fetchData()
            } else {
                // 로그인 실패
                finish() // todolist 화면이 보이지 않도록 앱종료
            }
        }
    }

    fun login() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build()
        )

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN
        )
    }

    fun logout() {
        AuthUI.getInstance()
            .signOut(this) // 로그아웃을 시도
            .addOnCompleteListener {  // 로그아웃을 성공했을 때 무엇을 할 것인지
                // 로그인 화면 다시 띄우기
                login()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.action_log_out -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

data class Todo(val text: String, var isDone: Boolean = false)

class TodoAdapter(
    private var dataSet: List<DocumentSnapshot>,
    val onClickDeleteIcon: (todo: DocumentSnapshot) -> Unit,
    val onClickItem: (todo: DocumentSnapshot) -> Unit
) :
    RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoAdapter.TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(ItemTodoBinding.bind(view))
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todo = dataSet[position]
        holder.binding.todoText.text = todo.getString("text") ?: ""

        if ((todo.getBoolean("isDone")?: false) == true) {
            holder.binding.todoText.apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG//가운데줄
                setTypeface(null, Typeface.ITALIC)
            }

        } else {
            holder.binding.todoText.apply {
                paintFlags = 0 // 0은 nomal, 일반 글자
                setTypeface(null, Typeface.NORMAL)
            }
        }
        holder.binding.deleteImageView.setOnClickListener {
            onClickDeleteIcon.invoke(todo)
        }
        holder.binding.root.setOnClickListener {
            onClickItem.invoke(todo)
        }
    }

    override fun getItemCount() = dataSet.size

    fun setData(newData: List<DocumentSnapshot>) {
        dataSet = newData
        notifyDataSetChanged()
    }
}

// 데이터 관련된 코드를 뷰모델이 관리하도록
class MainViewModel : ViewModel() {
    val db = Firebase.firestore

    // Mutable 상태 변경 가능하고, 관찰 가능한 라이브 데이터를 리스트 투두라는 타입으로 저장객체
    val todoLiveData = MutableLiveData<List<DocumentSnapshot>>()

    init {
        fetchData()
    }

    fun fetchData() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) { // null check
            db.collection(user.uid)
                .addSnapshotListener { value, e -> // e는 에러, 에러가 되면 종료를 리턴
                    if (e != null) {
                        return@addSnapshotListener
                    }
                    if(value != null) {
                        todoLiveData.value = value.documents
                    }
                }
        }
    }

    fun toggleTodo(todo: DocumentSnapshot) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            val isDone = todo.getBoolean("isDone")?: false
            db.collection(user.uid).document(todo.id).update("isDone", !isDone)
        }// isDone 을 반대 값으로 바꾸는 코드
    }

    fun addTodo(todo: Todo) {
        FirebaseAuth.getInstance().currentUser?.let{ user ->
            db.collection(user.uid).add(todo)
        }
    }

    fun deleteTodo(todo: DocumentSnapshot) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            db.collection(user.uid).document(todo.id).delete()
        }
    }
}