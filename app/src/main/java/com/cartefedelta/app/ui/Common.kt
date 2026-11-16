package com.cartefedelta.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cartefedelta.app.data.AppDatabase
import com.cartefedelta.app.data.Card
import com.cartefedelta.app.data.Photos

suspend fun deleteCard(context: Context, card: Card) {
    AppDatabase.get(context).cards().delete(card)
    Photos.delete(context, card.frontPhoto)
    Photos.delete(context, card.backPhoto)
}

/** Menu che compare tenendo premuta una carta. */
@Composable
fun CardActionsSheet(
    card: Card,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        ConfirmDelete(card.name, onConfirm = onDelete, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(card.name) },
        text = {
            Column {
                TextButton(onClick = onFavorite) {
                    Text(if (card.favorite) "Togli dalle preferite" else "Aggiungi alle preferite")
                }
                TextButton(onClick = onEdit) { Text("Modifica") }
                TextButton(onClick = { confirm = true }) { Text("Elimina") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

@Composable
fun ConfirmDelete(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminare «$name»?") },
        text = { Text("La carta e le sue foto verranno cancellate dal telefono.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Elimina") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
