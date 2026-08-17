package com.caloly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caloly.app.presentation.theme.CalolyTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalolyTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Caloly ve sağlık verilerin", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Caloly; günlük adım sayını, aktif kalorini ve toplam yakılan kaloriyi yalnızca günlük aktivite özetini göstermek için Health Connect üzerinden okur."
                    )
                    Text(
                        "Bu veriler sen izin vermeden okunmaz. Health Connect izinlerini istediğin zaman Android ayarlarından değiştirebilir veya kaldırabilirsin."
                    )
                    Text(
                        "Bu geliştirme sürümünde sağlık verileri cihazdaki Caloly ekranında kullanılır; sosyal paylaşım özelliği eklendiğinde hangi verilerin kimlerle paylaşılacağını ayrıca sen belirleyeceksin."
                    )
                }
            }
        }
    }
}
