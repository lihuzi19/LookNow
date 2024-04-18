package com.lihuzi.looknow

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.lihuzi.looknow.databinding.ActMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val binding = DataBindingUtil.setContentView<ActMainBinding>(this, R.layout.act_main)
            binding.tvMain.setOnClickListener {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 100)
            }
        } else {
            startActivity(Intent(this, CameraPreviewActivity::class.java))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 100 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, CameraPreviewActivity::class.java))
        } else {
            Toast.makeText(this, "请设置相机权限后重试", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
