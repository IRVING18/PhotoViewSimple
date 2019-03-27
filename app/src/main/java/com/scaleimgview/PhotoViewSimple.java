package com.scaleimgview;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

public class PhotoViewSimple extends View {
    private static final float IMAGE_WIDTH = Utils.dpToPixel(300);
    //放大系数，在最大放大的程度再增加一个倍数
    private static final float OVER_SCALE_FACTOR = 1.5f;

    Bitmap bitmap;
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    //初始化偏移，就是让图片居中到view中的
    float originalOffsetX;
    float originalOffsetY;

    //用来手指拖动的
    float transOffsetX;
    float transOffsetY;

    //最小缩放,这默认是看宽高谁更容易贴边，更容易贴边的比例
    float smallScale;
    //最大缩放，更不容易贴边的比例
    float bigScale;

    //判断当前是否最大
    boolean isBig;

    //缩放比例
    float scaleFraction;//0-1
    //缩放动画
    ObjectAnimator scaleAnimator;

    //手势监测
    GestureDetectorCompat mDetectorCompat;
    //配合惯性滑动
    //和Scroller 区别：1、Scroller 初始速度有问题，尽量不用
    //2、OverScroller.fling() 可以设置过度范围。弹回效果。
    OverScroller mOverScroller;

    public PhotoViewSimple(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    private void init() {
        bitmap = Utils.getAvatar(getResources(), (int) IMAGE_WIDTH);

        mDetectorCompat = new GestureDetectorCompat(getContext(), mGestureListener);
        //设置双击监听
        mDetectorCompat.setOnDoubleTapListener(mOnDoubleTapListener);
        mOverScroller = new OverScroller(getContext());
    }

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
         * 1、设置双击之后，这个方法是用来替代onSingleTapUp()的。
         * 因为设置双击之后，onSingleTapUp()，双击单机都会回调。
         * 2、这时用此方法来处理单机事件。
         * @param e
         * @return 没用
         */
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return false;
        }

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

        /**
         * 早期谷歌地图，双击不抬起滑动，设置3维变化
         * 1、也就是双击同样被调用，区别就是双击后不抬起的话，之后的触摸事件move等等仍然回调给这个方法，直到up事件。
         * @param e
         * @return 没用
         */
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return false;
        }
    };

    /**
     * 手势监听回调
     */
    GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        /**
         * 确定是否消费事件
         * @return true :消费，false：不消费
         * 不消费的话其他回调都不会收到
         */
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        /**
         * 预按下，100ms 到达
         * todo:上一期的预按下回顾
         * @param e
         */
        @Override
        public void onShowPress(MotionEvent e) {

        }

        /**
         * 按下抬起，单机，<p>
         * 1、默认长按开启的话，超过500ms再抬起就不会收到回调了。
         * 2、如果设置关闭长按，那么就没有500ms限制了，随时抬起都会有回调。，mDetectorCompat.setIsLongpressEnabled(false);
         * @param e
         * @return 返回值没有用
         */
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

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

        /**
         * 长按回调，500ms
         * @param e
         */
        @Override
        public void onLongPress(MotionEvent e) {

        }

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

//                //相当于手动加动画了
//                for (int i = 10; i < 100; i += 10) {
//                    postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            refreshFling();
//                        }
//                    }, i);
//                }

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


    /**
     * 获取view大小
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        originalOffsetX = ((float) getWidth() - bitmap.getWidth()) / 2;
        originalOffsetY = ((float) getHeight() - bitmap.getHeight()) / 2;

        //判断当前bitmap相对view来说，到底是宽更容易到view边界，还是高度更先到view边界。
        //宽度更容易到view边界
        if ((float) bitmap.getWidth() / bitmap.getHeight() > (float) getWidth() / getHeight()) {
            smallScale = (float) getWidth() / bitmap.getWidth();
            bigScale = (float) getHeight() / bitmap.getHeight() * OVER_SCALE_FACTOR;
        } else {
            smallScale = (float) getHeight() / bitmap.getHeight();
            bigScale = (float) getWidth() / bitmap.getWidth() * OVER_SCALE_FACTOR;

        }
    }

    /**
     * 绘制
     *
     * @param canvas
     */
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

    public float getScaleFraction() {
        return scaleFraction;
    }

    public void setScaleFraction(float scaleFraction) {
        invalidate();
        this.scaleFraction = scaleFraction;
    }
}
