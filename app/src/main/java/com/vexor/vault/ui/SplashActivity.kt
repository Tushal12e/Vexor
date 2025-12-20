package com.vexor.vault.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivitySplashBinding
import com.vexor.vault.security.VaultPreferences

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    
    private lateinit var binding: ActivitySplashBinding
    private lateinit var prefs: VaultPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        
        // Animate logo
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 800
        binding.ivLogo.startAnimation(fadeIn)
        binding.tvAppName.startAnimation(fadeIn)
        binding.tvTagline.startAnimation(fadeIn)
        
        // Navigate after delay
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = if (prefs.isFirstSetupComplete) {
                Intent(this, AuthActivity::class.java)
            } else {
                Intent(this, SetupActivity::class.java)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 1500)
    }
}
