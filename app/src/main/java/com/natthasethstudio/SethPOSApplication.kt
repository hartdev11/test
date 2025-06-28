package com.natthasethstudio.sethpos

import android.app.Application
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.google.firebase.FirebaseApp

class SethPOSApplication : Application() {
    var feedAdapter: FeedAdapter? = null

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        
        // Configure Glide for better performance
        Glide.init(this, GlideBuilder().apply {
            setMemoryCache(LruResourceCache(1024 * 1024 * 20)) // 20MB memory cache
            setDiskCache(ExternalPreferredCacheDiskCacheFactory(this@SethPOSApplication, "images", 1024 * 1024 * 100)) // 100MB disk cache
        })
    }
} 