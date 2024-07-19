package com.lihuzi.looknow

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.lihuzi.looknow.databinding.ActMainBinding

class MainActivity : AppCompatActivity() {

    private val permissionList =
        arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActMainBinding>(this, R.layout.act_main)
        binding.tvMain.setOnClickListener {
            requestPermissions(permissionList.toTypedArray(), 100)
        }
        if (checkoutPermissionList()) {
            startActivity(Intent(this, PreviewActivity::class.java))
        }
    }

    private fun checkoutPermissionList(): Boolean {
        permissionList.forEach {
            if (checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 100 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, PreviewActivity::class.java))
        } else {
            Toast.makeText(this, "请设置相机权限后重试", Toast.LENGTH_SHORT).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
