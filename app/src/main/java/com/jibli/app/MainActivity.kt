package com.jibli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private val auth get() = FirebaseAuth.getInstance()
private val db get() = FirebaseFirestore.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { App() }
                }
            }
        }
    }
}

@Composable
fun App() {
    var loggedIn by remember { mutableStateOf(auth.currentUser != null) }
    var creating by remember { mutableStateOf(false) }
    when {
        !loggedIn -> AuthScreen { loggedIn = true }
        creating -> CreateScreen { creating = false }
        else -> HomeScreen(onCreate = { creating = true }, onLogout = { auth.signOut(); loggedIn = false })
    }
}

@Composable
fun AuthScreen(onDone: () -> Unit) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isProvider by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("جيبلي", style = MaterialTheme.typography.displaySmall)
        Text("شو بدك؟ جيبلي.")
        if (signup) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(city, { city = it }, label = { Text("المدينة") }, modifier = Modifier.fillMaxWidth())
            Row { Checkbox(isProvider, { isProvider = it }); Text("أنا مقدم خدمة / بائع", Modifier.padding(top = 12.dp)) }
        }
        OutlinedTextField(email, { email = it }, label = { Text("البريد الإلكتروني") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("كلمة السر (6 أحرف على الأقل)") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            error = ""; busy = true
            val fail = { e: Exception -> error = "فشلت العملية: ${e.localizedMessage}"; busy = false }
            if (signup) {
                auth.createUserWithEmailAndPassword(email.trim(), pass).addOnSuccessListener { r ->
                    db.collection("users").document(r.user!!.uid).set(mapOf(
                        "name" to name.trim(), "city" to city.trim(), "email" to email.trim(),
                        "accountType" to if (isProvider) "provider" else "user",
                        "availableNow" to false, "createdAt" to FieldValue.serverTimestamp()
                    )).addOnSuccessListener { onDone() }.addOnFailureListener(fail)
                }.addOnFailureListener(fail)
            } else {
                auth.signInWithEmailAndPassword(email.trim(), pass)
                    .addOnSuccessListener { onDone() }.addOnFailureListener(fail)
            }
        }) { Text(if (signup) "إنشاء حساب" else "تسجيل الدخول") }
        TextButton(onClick = { signup = !signup; error = "" }) {
            Text(if (signup) "عندي حساب" else "ما عندي حساب؟ أنشئ حساب")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onCreate: () -> Unit, onLogout: () -> Unit) {
    var items by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        val reg = db.collection("requests").orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { snap, e ->
                if (e != null) error = "تعذّر تحميل الطلبات، تحقق من الإنترنت"
                else { error = ""; items = snap?.documents?.map { it.data.orEmpty() + ("id" to it.id) }.orEmpty() }
            }
        onDispose { reg.remove() }
    }
    val shown = items.filter {
        query.isBlank() || "${it["title"]} ${it["description"]} ${it["city"]}".contains(query, true)
    }
    Scaffold(floatingActionButton = {
        ExtendedFloatingActionButton(onClick = onCreate) { Text("+ شو بدك؟") }
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("جيبلي", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onLogout) { Text("خروج") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("ابحث عن طلب") }, modifier = Modifier.fillMaxWidth())
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            if (shown.isEmpty() && error.isEmpty()) Text("ما في طلبات بعد", Modifier.padding(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(shown, key = { it["id"] as String }) { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${r["title"]}", style = MaterialTheme.typography.titleMedium)
                            Text("${r["description"]}")
                            Text("${r["city"]} • ${r["category"]} • الميزانية: ${r["budget"]}")
                            Text("${r["ownerName"]} • ${r["urgency"]} • عروض: ${r["offersCount"]}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateScreen(onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("أخرى") }
    var urgency by remember { mutableStateOf("عادي") }
    var negotiable by remember { mutableStateOf("أريد عروض أسعار") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    @Composable
    fun Choice(label: String, options: List<String>, value: String, set: (String) -> Unit) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = value == o, onClick = { set(o) }, label = { Text(o) }) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("شو بدك؟", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, label = { Text("عنوان الطلب") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(desc, { desc = it }, label = { Text("وصف ما تحتاجه") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(city, { city = it }, label = { Text("المدينة / المنطقة") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(budget, { budget = it }, label = { Text("الميزانية") }, modifier = Modifier.fillMaxWidth())
        Choice("التصنيف", listOf("جوالات", "خدمات", "أخرى"), category) { category = it }
        Choice("الاستعجال", listOf("عادي", "مستعجل"), urgency) { urgency = it }
        Choice("السعر", listOf("نعم تفاوض", "لا", "أريد عروض أسعار"), negotiable) { negotiable = it }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            if (title.isBlank() || city.isBlank()) { error = "العنوان والمدينة مطلوبان"; return@Button }
            busy = true; error = ""
            val uid = auth.currentUser!!.uid
            db.collection("users").document(uid).get().addOnSuccessListener { u ->
                db.collection("requests").add(mapOf(
                    "ownerId" to uid, "ownerName" to (u.getString("name") ?: ""),
                    "title" to title.trim(), "description" to desc.trim(), "city" to city.trim(),
                    "budget" to budget.trim(), "category" to category, "urgency" to urgency,
                    "negotiable" to negotiable, "status" to "open", "offersCount" to 0,
                    "createdAt" to FieldValue.serverTimestamp()
                )).addOnSuccessListener { onDone() }
                  .addOnFailureListener { error = "فشل النشر: ${it.localizedMessage}"; busy = false }
            }.addOnFailureListener { error = "فشل النشر، تحقق من الإنترنت"; busy = false }
        }) { Text("نشر الطلب") }
        TextButton(onClick = onDone) { Text("رجوع") }
    }
}
