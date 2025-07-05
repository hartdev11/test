package com.natthasethstudio.sethpos

import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.*

class AnimatedAnimalsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val animals = listOf(
        R.drawable.animated_cat,
        R.drawable.animated_dog,
        R.drawable.animated_bird,
        R.drawable.animated_rabbit
    )
    
    private val random = Random()
    private var currentAnimal: ImageView? = null
    private var isAnimating = false
    
    // Interaction states
    private var isLiked = false
    private var isCommented = false
    private var isBoosted = false
    
    // UI elements for interactions
    private var heartView: ImageView? = null
    private var speechBubble: TextView? = null
    private var foodView: ImageView? = null

    init {
        // Set background to transparent
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    // Public methods for user interactions
    fun onLikePressed() {
        isLiked = true
        showHeartAnimation()
    }
    
    fun onLikeUnpressed() {
        isLiked = false
        showSadFaceAnimation()
    }
    
    fun onCommentPressed() {
        // Only show speech bubble if not already commented
        if (!isCommented) {
            isCommented = true
            showSpeechBubble()
            pauseAnimalAnimation()
        }
    }
    
    fun onCommentCancelled() {
        // Called when user cancels comment without actually commenting
        if (isCommented) {
            hideSpeechBubble()
            isCommented = false
            resumeAnimalMovement(currentAnimal)
        }
    }
    
    fun onBoostPressed() {
        isBoosted = true
        android.util.Log.d("AnimatedAnimalsView", "onBoostPressed called - currentAnimal: ${currentAnimal != null}")
        showFoodAnimation()
    }
    
    fun onBoostUnpressed() {
        isBoosted = false
        android.util.Log.d("AnimatedAnimalsView", "onBoostUnpressed called - currentAnimal: ${currentAnimal != null}")
        showConfusedAnimation()
    }
    
    fun onCommentFinished() {
        // Called when user finishes commenting and returns to feed
        if (isCommented) {
            hideSpeechBubble()
            isCommented = false
            resumeAnimalMovement(currentAnimal)
        }
    }
    
    private fun showHeartAnimation() {
        currentAnimal?.let { animal ->
            // Make animal happy first
            animal.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(200)
                .withEndAction {
                    animal.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
            
            // Create multiple hearts
            repeat(3) { index ->
                val heart = ImageView(context).apply {
                    layoutParams = LayoutParams(
                        (24 * resources.displayMetrics.density).toInt(),
                        (24 * resources.displayMetrics.density).toInt()
                    )
                    setImageResource(R.drawable.ic_heart_filled)
                    setColorFilter(ContextCompat.getColor(context, R.color.red))
                    x = animal.x + animal.width / 2f - width / 2f + (index - 1) * 20f
                    y = animal.y - height - 20f
                    alpha = 0f
                    scaleX = 0.3f
                    scaleY = 0.3f
                }
                
                addView(heart)
                
                // Staggered animation for each heart
                heart.postDelayed({
                    heart.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .y(heart.y - 80f - index * 20f)
                        .setDuration(800)
                        .withEndAction {
                            heart.animate()
                                .alpha(0f)
                                .setDuration(400)
                                .withEndAction {
                                    removeView(heart)
                                }
                                .start()
                        }
                        .start()
                }, index * 150L)
            }
        }
    }
    
    private fun showSpeechBubble() {
        currentAnimal?.let { animal ->
            // Create speech bubble
            val bubble = TextView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                )
                text = getRandomComment()
                setTextColor(ContextCompat.getColor(context, R.color.black))
                setTextSize(12f)
                setPadding(20, 10, 20, 10)
                background = ContextCompat.getDrawable(context, R.drawable.speech_bubble_bg)
                x = animal.x + animal.width + 10f
                y = animal.y - height - 10f
                alpha = 0f
            }
            
            addView(bubble)
            speechBubble = bubble
            
            // Animate speech bubble
            bubble.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }
    
    private fun hideSpeechBubble() {
        speechBubble?.let { bubble ->
            bubble.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    removeView(bubble)
                    speechBubble = null
                }
                .start()
        }
    }
    
    private fun showFoodAnimation() {
        currentAnimal?.let { animal ->
            android.util.Log.d("AnimatedAnimalsView", "showFoodAnimation called - animal tag: ${animal.tag}")
            
            // Pause animal movement first
            animal.animate().cancel()
            
            // Determine which food to show based on current animal
            val foodResource = when (animal.tag) {
                R.drawable.animated_dog -> {
                    android.util.Log.d("AnimatedAnimalsView", "Showing dog food")
                    R.drawable.ic_food_dog
                }
                R.drawable.animated_cat -> {
                    android.util.Log.d("AnimatedAnimalsView", "Showing cat food")
                    R.drawable.ic_food_cat
                }
                R.drawable.animated_bird -> {
                    android.util.Log.d("AnimatedAnimalsView", "Showing bird food")
                    R.drawable.ic_food_bird
                }
                R.drawable.animated_rabbit -> {
                    android.util.Log.d("AnimatedAnimalsView", "Showing rabbit food")
                    R.drawable.ic_food_rabbit
                }
                else -> {
                    android.util.Log.d("AnimatedAnimalsView", "Showing default food (carrot)")
                    R.drawable.ic_food_rabbit // Default to carrot
                }
            }
            
            // Create food view
            val food = ImageView(context).apply {
                layoutParams = LayoutParams(
                    (40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                )
                setImageResource(foodResource)
                x = animal.x + animal.width / 2f - width / 2f
                y = animal.y - height - 30f
                alpha = 0f
                scaleX = 0.5f
                scaleY = 0.5f
            }
            
            addView(food)
            foodView = food
            
            // Animate food falling
            food.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .y(animal.y + animal.height / 2f)
                .setDuration(800)
                .withEndAction {
                    // Animal eating animation - make it bigger
                    animal.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(300)
                        .withEndAction {
                            // Stay big for a moment
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                animal.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(300)
                                    .withEndAction {
                                        // Remove food
                                        food.animate()
                                            .alpha(0f)
                                            .setDuration(300)
                                            .withEndAction {
                                                removeView(food)
                                                foodView = null
                                                // Resume animal movement
                                                resumeAnimalMovement(animal)
                                            }
                                            .start()
                                    }
                                    .start()
                            }, 500)
                        }
                        .start()
                }
                .start()
        }
    }
    
    private fun pauseAnimalAnimation() {
        currentAnimal?.let { animal ->
            // Pause the animal's movement
            animal.animate().cancel()
            
            // Add a small bounce effect to show it stopped
            animal.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(200)
                .withEndAction {
                    animal.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
    }
    
    private fun resumeAnimalAnimation() {
        // Resume normal movement after a delay
        postDelayed({
            if (isCommented) {
                hideSpeechBubble()
                isCommented = false
                // Continue the animation from current position
                currentAnimal?.let { animal ->
                    animal.animate()
                        .x(-100f)
                        .setDuration(3000)
                        .setInterpolator(android.view.animation.LinearInterpolator())
                        .withEndAction {
                            removeView(animal)
                            currentAnimal = null
                            isAnimating = false
                        }
                        .start()
                }
            }
        }, 2000)
    }
    
    private fun resumeAnimalMovement(animal: ImageView?) {
        animal?.let { anim ->
            // Resume animal movement from current position
            val screenWidth = resources.displayMetrics.widthPixels
            anim.animate()
                .x(-100f)
                .setDuration(3000)
                .setInterpolator(android.view.animation.LinearInterpolator())
                .withEndAction {
                    removeView(anim)
                    currentAnimal = null
                    isAnimating = false
                }
                .start()
        }
    }
    
    private fun showSadFaceAnimation() {
        currentAnimal?.let { animal ->
            // Make animal sad
            animal.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(300)
                .withEndAction {
                    animal.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .start()
                }
                .start()
            
            // Show sad emoji
            val sadEmoji = TextView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                )
                text = "😢"
                textSize = 24f
                x = animal.x + animal.width / 2f - 20f
                y = animal.y - height - 30f
                alpha = 0f
            }
            
            addView(sadEmoji)
            
            sadEmoji.animate()
                .alpha(1f)
                .y(sadEmoji.y - 50f)
                .setDuration(1000)
                .withEndAction {
                    sadEmoji.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            removeView(sadEmoji)
                        }
                        .start()
                }
                .start()
        }
    }
    
    private fun showConfusedAnimation() {
        android.util.Log.d("AnimatedAnimalsView", "showConfusedAnimation called")
        currentAnimal?.let { animal ->
            // Make animal confused
            animal.animate()
                .rotation(5f)
                .setDuration(200)
                .withEndAction {
                    animal.animate()
                        .rotation(-5f)
                        .setDuration(200)
                        .withEndAction {
                            animal.animate()
                                .rotation(0f)
                                .setDuration(200)
                                .start()
                        }
                        .start()
                }
                .start()
            
            // Show question mark
            val questionMark = TextView(context).apply {
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                )
                text = "❓"
                textSize = 20f
                x = animal.x + animal.width + 10f
                y = animal.y - height - 10f
                alpha = 0f
            }
            
            addView(questionMark)
            
            questionMark.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        questionMark.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .withEndAction {
                                removeView(questionMark)
                            }
                            .start()
                    }, 1000)
                }
                .start()
        }
    }
    
    private fun getRandomComment(): String {
        val comments = listOf(
            "น่ารักมาก! 😊",
            "ชอบเลย! ❤️",
            "สวยจัง! ✨",
            "เจ๋งมาก! 🎉",
            "สุดยอด! 👏",
            "น่าเอ็นดู! 🥰"
        )
        return comments[random.nextInt(comments.size)]
    }

    fun showRandomAnimal() {
        if (isAnimating) return
        
        // Remove previous animal if exists
        currentAnimal?.let { removeView(it) }
        
        val animalImageView = ImageView(context).apply {
            layoutParams = LayoutParams(
                (48 * resources.displayMetrics.density).toInt(),
                (36 * resources.displayMetrics.density).toInt()
            )
            val randomAnimal = animals[random.nextInt(animals.size)]
            setImageResource(randomAnimal)
            tag = randomAnimal // Set tag to identify the animal type
            val drawable = drawable
            if (drawable is AnimatedVectorDrawable) {
                drawable.start()
            }
        }
        currentAnimal = animalImageView
        addView(animalImageView)
        val screenWidth = resources.displayMetrics.widthPixels
        val duration = if (random.nextInt(2) == 0) 7000L else 6000L
        val movementPattern = random.nextInt(4)
        when (movementPattern) {
            0 -> {
                // Always go left - start from right
                animalImageView.x = screenWidth.toFloat() + 100f
                animalImageView.y = (random.nextInt(150) + 50).toFloat()
                
                animalImageView.animate()
                    .x(-100f)
                    .setDuration(duration)
                    .setInterpolator(android.view.animation.LinearInterpolator())
                    .withEndAction {
                        removeView(animalImageView)
                        currentAnimal = null
                        isAnimating = false
                    }
                    .start()
            }
            1 -> {
                // Always go left - start from right
                animalImageView.x = screenWidth.toFloat() + 100f
                animalImageView.y = (random.nextInt(150) + 50).toFloat()
                
                animalImageView.animate()
                    .x(-100f)
                    .setDuration(duration)
                    .setInterpolator(android.view.animation.LinearInterpolator())
                    .withEndAction {
                        removeView(animalImageView)
                        currentAnimal = null
                        isAnimating = false
                    }
                    .start()
            }
            2 -> {
                // Always go left - diagonal
                animalImageView.x = screenWidth.toFloat() + 100f
                animalImageView.y = (random.nextInt(100) + 50).toFloat()
                
                animalImageView.animate()
                    .x(-100f)
                    .y((random.nextInt(100) + 50).toFloat())
                    .setDuration(duration)
                    .setInterpolator(android.view.animation.LinearInterpolator())
                    .withEndAction {
                        removeView(animalImageView)
                        currentAnimal = null
                        isAnimating = false
                    }
                    .start()
            }
            3 -> {
                // Always go left - bounce
                animalImageView.x = screenWidth.toFloat() + 100f
                animalImageView.y = (random.nextInt(100) + 50).toFloat()
                
                val bounceAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    this.duration = 7000L
                    addUpdateListener { animator ->
                        val progress = animator.animatedValue as Float
                        animalImageView.x = screenWidth.toFloat() + 100f - (screenWidth.toFloat() + 200f) * progress
                        animalImageView.translationY = (Math.sin(progress * Math.PI * 4) * 30).toFloat()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            removeView(animalImageView)
                            currentAnimal = null
                            isAnimating = false
                        }
                    })
                }
                bounceAnimator.start()
            }
        }
        isAnimating = true
    }
    
    fun startRandomAnimalSpawn() {
        // Spawn first animal immediately
        showRandomAnimal()
        scheduleNextSpawn()
    }
    
    private fun scheduleNextSpawn() {
        // Schedule next animal spawn in 3-7 seconds (less frequent)
        val nextSpawnDelay = (3000 + random.nextInt(4000)).toLong()
        postDelayed({
            if (!isAnimating) {
                showRandomAnimal()
            }
            scheduleNextSpawn()
        }, nextSpawnDelay)
    }
    
    fun stop() {
        removeAllViews()
        currentAnimal = null
        isAnimating = false
        // Reset interaction states
        isLiked = false
        isCommented = false
        isBoosted = false
    }
} 