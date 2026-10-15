package com.example.lab08

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.ClearCredentialStateRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun CloudAccountDialog(cloud: CloudSync, onDismiss: () -> Unit) {
    val session by cloud.session.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    // La contraseña no se guarda en SavedState ni en Room.
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Sincronización en la nube") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (session.uid != null) {
                Text(session.email ?: "Cuenta conectada")
                Text(session.message)
                Text("Tus tareas se guardan en tu cuenta de Firebase y se actualizan en los dispositivos donde inicies sesión con la misma cuenta.")
                TextButton(onClick = { cloud.requestSync() }) { Text("Sincronizar ahora") }
                TextButton(onClick = {
                    cloud.signOut()
                    scope.launch { runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) } }
                }) { Text("Cerrar sesión") }
                Text("Cerrar sesión conserva las tareas en este dispositivo.", style = MaterialTheme.typography.bodySmall)
            } else if (!session.configured) {
                Text("Falta conectar este proyecto con Firebase. Agrega el archivo google-services.json de la aplicación.")
            } else {
                Text("Inicia sesión con la misma cuenta en tus dispositivos. Las tareas que ya tienes se conservarán.")
                Button(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val resource = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                            check(resource != 0) { "Google no está habilitado en Firebase" }
                            val option = GetGoogleIdOption.Builder().setServerClientId(context.getString(resource))
                                .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build()
                            val response = CredentialManager.create(context).getCredential(context,
                                GetCredentialRequest.Builder().addCredentialOption(option).build())
                            val credential = response.credential
                            check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                            cloud.signInGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                            password = ""
                        } catch (_: Exception) {
                            error = "No se pudo iniciar con Google. Comprueba que haya una cuenta Google en el dispositivo o usa correo y contraseña."
                        } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Continuar con Google") }
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("cloudEmail"))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña de esta app") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cloudPassword"))
                Text("Para usar correo, crea una cuenta de esta app; no necesitas usar la contraseña de tu Google.",
                    style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
                        busy = true; error = null
                        scope.launch {
                            try { cloud.signInEmail(email, password, false); password = "" }
                            catch (_: Exception) { error = "No se pudo iniciar sesión. Revisa el correo, la contraseña y la conexión." }
                            finally { busy = false }
                        }
                    }) { Text("Iniciar sesión") }
                    TextButton(enabled = !busy && email.isNotBlank() && password.length >= 6, onClick = {
                        busy = true; error = null
                        scope.launch {
                            try { cloud.signInEmail(email, password, true); password = "" }
                            catch (_: Exception) { error = "No se pudo crear la cuenta. Usa un correo válido y una contraseña de al menos 6 caracteres." }
                            finally { busy = false }
                        }
                    }) { Text("Crear cuenta") }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cerrar") } })
}
