# PhotoViewSimple
手写photoView

# 一、实现思路
#### 1、实现双击缩放 
- 1.1 canvas.**scale()** 来实现 
- 1.2 监听双击事件 GestureDetectorCompat 的 **OnDoubleTapListener** 的 **onDoubleTapEvent()**方法
- 1.3 同时通过控制scaleFraction系数，来控制一个动画，让变化过程更柔顺。
#### 2、实现拖动
- 2.1 canvas.**translate()** 来实现
- 2.2 监听触摸事件 GestureDetectorCompat 的 **OnGestureListener** 的 **onScroll()** 方法可以监听手势滑动。
#### 3、实现惯性滑动
- 3.1 监听触摸事件 GestureDetectorCompat 的 **OnGestureListener** 的 **onFling()** 方法可以监听手势惯性。
- 3.2 通过 **OverScroller** 惯性计算模型，可以帮助计算惯性值。
#### 4、实现双指放缩
- 4.1 设置ScaleGestureDetector，监听缩放数值。
- 4.2 onScale()只能在一次事件流中累计缩放值，如果要拿到总的缩放值，那么在onScaleBegin()记录下上次的缩放值作为基数，然后就可以计算总的缩放值了。
- 4.3 有了总的缩放值之后就可以计算scaleFraction系数了。
- 4.4 在onTouchEvent中需要控制什么时候把event传给ScaleGestureDetector还是GestureDetectorCompat。
在这我们直接先给ScaleGestureDetector，然后判断isInprocess()，只有两个手指以上的时候才会返回ture

[基础知识](https://github.com/IRVING18/notes/blob/master/android/自定义View/A12、手势触摸-初探.md)

# 二、实现代码

[具体项目](https://github.com/IRVING18/PhotoViewSimple)

关键代码  
## 1、实现双击缩放
#### 1.1 draw()
```java
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //缩放
        float scale = smallScale + (bigScale - smallScale) * scaleFraction;
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        //画bitmap
        canvas.drawBitmap(bitmap, originalOffsetX, originalOffsetY, mPaint);
    }
```
#### 1.2 监听双击事件
> 需要先重写onTouchEvent()方法，然后将event直接交给GestureDetector处理。然后我们的逻辑写在GestureDetector中    
#### 1.3 创建动画来控制 scaleFraction 实现过度效果，
```java
    /**
     * 重写触摸监听，并把事件传递给GestureDetectorCompat处理
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //把事件交给GestureDetectorCompat，他的onDown事件要返回true，才能真正监听到。
        return mDetectorCompat.onTouchEvent(event);
    }

    /**
     * 双击回调监听
     */
    GestureDetector.OnDoubleTapListener mOnDoubleTapListener = new GestureDetector.OnDoubleTapListener() {
        /**
         * 1、双击，两次点击不超过300ms，
         * 但是连续点击4次，会触发两次。
         * 2、两次点击小于40ms ，会被认为手抖，不会触发
         * @param e
         * @return 没用
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            isBig = !isBig;
            if (isBig) {
                getScaleAnimator().start();
            } else {
                getScaleAnimator().reverse();
            }
            return false;
        }
    };
    
    /**
     * 动画初始化
     *
     * @return
     */
    private ObjectAnimator getScaleAnimator() {
        if (scaleAnimator == null) {
            scaleAnimator = ObjectAnimator.ofFloat(this, "scaleFraction", 0, 1);
        }
        return scaleAnimator;
    }
```

## 2、实现拖动
#### 2.1 translate()
```java
 /**
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //只有在放大的情况再拖动
        if (isBig) {
            //手指拖动
            canvas.translate(transOffsetX, transOffsetY);
        }
        //缩放
        float scale = smallScale + (bigScale - smallScale) * scaleFraction;
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        //画bitmap
        canvas.drawBitmap(bitmap, originalOffsetX, originalOffsetY, mPaint);
    }
```
#### 2.2 获取移动数值
> 通过**OnGestureListener** 的 **onScroll()** 方法可以监听手势滑动。    
```java
    /**
     * 手势监听回调
     */
    GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        /**
         * 手指滑动
         * @param down 按下时
         * @param event 移动后
         * @param distanceX 这个x偏移是：lastEvent.x - nowEvent.x = distanceX
         * @param distanceY 同上 也就是他是 起始点 - 终点 所以transOffsetX才用的 -=
         * @return
         */
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent event, float distanceX, float distanceY) {
            //只有放大时可拖动
            if (isBig) {
                transOffsetX -= distanceX;
                //设置不能拖出屏幕
                //x的最大值
                transOffsetX = Math.min(transOffsetX, (bitmap.getWidth() * bigScale - (float) getWidth()) / 2);
                //x的最小值
                transOffsetX = Math.max(transOffsetX, -(bitmap.getWidth() * bigScale - (float) getWidth()) / 2);

                transOffsetY -= distanceY;
                transOffsetY = Math.min(transOffsetY, (bitmap.getHeight() * bigScale - (float) getHeight()) / 2);
                transOffsetY = Math.max(transOffsetY, -(bitmap.getHeight() * bigScale - (float) getHeight()) / 2);
//            Log.e("wzzzzzzzz", "onScroll: " + distanceX + "   " + distanceY + "  " + transOffsetY + "   " + transOffsetY);

                invalidate();
            }
            return false;
        }
    };

```
## 3、实现惯性滑动
#### 3.1 监听触摸事件 GestureDetectorCompat 的 **OnGestureListener** 的 **onFling()** 方法可以监听手势惯性。
#### 3.2 通过 **OverScroller** 惯性计算模型，可以帮助计算惯性值。
```java
    /**
     * 手势监听回调
     */
    GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        /**
         * 惯性滑动
         * @param velocityX 惯性越大，值越大
         * @return 返回值没有
         */
        @Override
        public boolean onFling(MotionEvent down, MotionEvent event, float velocityX, float velocityY) {
            if (isBig) {
                //这个就相当于惯性计算器
                mOverScroller.fling(
                        //起始点
                        (int) transOffsetX, (int) transOffsetY,
                        //惯性
                        (int) velocityX, (int) velocityY,
                        //最大值和最小值
                        -(int) (bitmap.getWidth() * bigScale - getWidth()) / 2,
                        (int) (bitmap.getWidth() * bigScale - getWidth()) / 2,
                        -(int) (bitmap.getHeight() * bigScale - (float) getHeight()) / 2,
                        (int) (bitmap.getHeight() * bigScale - (float) getHeight()) / 2,
                        //过度拉伸
                        100,
                        100
                );

                postOnAnimation(mFlingRunnable);
            }
            return false;
        }
    };
    /**
     * 惯性滑动，实现手动动画
     */
    Runnable mFlingRunnable = new Runnable() {
        @Override
        public void run() {
            //调用这个方法，启动计算。类似ParentView调用childView的onMeasure()方法。调用完，再取值。
            //computeScrollOffset返回值是boolean，是否还有惯性
            if (mOverScroller.computeScrollOffset()) {
                transOffsetX = mOverScroller.getCurrX();
                transOffsetY = mOverScroller.getCurrY();
                invalidate();
                //默认是每一帧执行一次，
                //1、和post(action)区别：post()立即去主线程执行，postOnAnimation()：等到下一帧再去主线程执行
                //2、兼容api16以下 : ViewCompat.postOnAnimation();
//                ViewCompat.postOnAnimation(ScaleImageView.this,mFlingRunnable);
                postOnAnimation(mFlingRunnable);

            }
        }
    };
```
## 4、实现双指放缩
#### 4.1 设置ScaleGestureDetector，监听缩放数值。
#### 4.2 onScale()只能在一次事件流中累计缩放值，如果要拿到总的缩放值，那么在onScaleBegin()记录下上次的缩放值作为基数，然后就可以计算总的缩放值了。
#### 4.3 有了总的缩放值之后就可以计算scaleFraction系数了。
```java
        /**
         * 双指缩放事件回调
         */
        mOnScaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener() {
            float cacheScale;

            /**
             * 获取放缩数值
             *
             * @param detector
             * @return true:重新计算scale，false：在一次down事件流中是累加的
             */
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                //获取放大系数,范围是从1开始的，且只有在同一事件流中scaleFactor的值才是累加的，两次事件流只能自己算。
                //这用的是cacheScale,每次事件流刚进来的时候先把上一次的基数留下来，然后乘上这次的scale，就是总scale
                totalScale = cacheScale * detector.getScaleFactor();
                if (smallScale <= totalScale && totalScale <= bigScale) {
                    scaleFraction = (totalScale - smallScale) / (bigScale - smallScale);
                    invalidate();
                }
                return false;
            }
            
            /**
             * 是否监听放缩手势
             *
             * @param detector
             * @return true:才能获取到放缩手势
             */
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                if (smallScale <= totalScale && totalScale <= bigScale) {
                    cacheScale = totalScale;
                } else if (smallScale > totalScale) {
                    cacheScale = smallScale;
                } else {
                    cacheScale = bigScale;
                }

                return true;
            }
        }
```
#### 4.2 在onTouchEvent中需要控制什么时候把event传给ScaleGestureDetector还是GestureDetectorCompat。
在这我们直接先给ScaleGestureDetector，然后判断isInprocess()，只有两个手指以上的时候才会返回ture
```java
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result;
        //把事件交给GestureDetectorCompat，他的onDown事件要返回true，才能真正监听到。
        result = mScaleDetector.onTouchEvent(event);
        //如果两指事件触发了，就不给双击传递了
        boolean inProgress = mScaleDetector.isInProgress();
        if (!inProgress) {
            result = mDetectorCompat.onTouchEvent(event);
        }

        return result;
    }
```