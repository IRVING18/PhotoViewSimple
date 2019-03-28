package com.scaleimgview;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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

    ///////////////////////////////////////////////////////////////////////////
    // 双击放大，移动功能
    ///////////////////////////////////////////////////////////////////////////
    //手势监测
    GestureDetectorCompat mDetectorCompat;
    //手势监听回调
    GestureDetector.OnGestureListener mGestureListener;
    // 双击回调监听
    GestureDetector.OnDoubleTapListener mOnDoubleTapListener;
    //配合惯性滑动
    //和Scroller 区别：1、Scroller 初始速度有问题，尽量不用
    //2、OverScroller.fling() 可以设置过度范围。弹回效果。
    OverScroller mOverScroller;
    // 惯性滑动，实现手动动画
    Runnable mFlingRunnable;

    ///////////////////////////////////////////////////////////////////////////
    // 双指缩放
    ///////////////////////////////////////////////////////////////////////////
    //双指缩放
    ScaleGestureDetector mScaleDetector;
    //双指缩放回调监听
    ScaleGestureDetector.OnScaleGestureListener mOnScaleGestureListener;

    public PhotoViewSimple(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    private void init() {
        bitmap = Utils.getAvatar(getResources(), (int) IMAGE_WIDTH);

        //手势监听
        mDetectorCompat = new GestureDetectorCompat(getContext(), mGestureListener);
        //设置双击监听
        mDetectorCompat.setOnDoubleTapListener(mOnDoubleTapListener);
        //filing惯性滑动计算器
        mOverScroller = new OverScroller(getContext());

        //双指缩放
        mScaleDetector = new ScaleGestureDetector(getContext(), mOnScaleGestureListener);
    }

    /**
     * 重写触摸监听，并把事件传递给GestureDetectorCompat处理
     *
     * @param event
     * @return
     */
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

    ///////////////////////////////////////////////////////////////////////////
    // 双击放大，放大拖动功能 start
    ///////////////////////////////////////////////////////////////////////////
    {
        /**
         * 双击回调监听
         */
        mOnDoubleTapListener = new GestureDetector.OnDoubleTapListener() {
            /**
             * 1、设置双击之后，这个方法是用来替代onSingleTapUp()的。
             * 因为设置双击之后，onSingleTapUp()，双击单机都会回调。
             * 2、这时用此方法来处理单机事件。
             *
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
             *
             * @param e
             * @return 没用
             */
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                isBig = !isBig;
                if (isBig) {
                    //用于处理点击放大之后，让手指触摸点在对应放大之后的点看起来是一个点。
                    // 也就是点眼睛，放大之后还想让眼睛在我手指点的位置
                    // 在这是因为translate的是画布，所以移动坐标是相对中心点来的，所以计算都是以(getWidth()/2,getHeight()/2)为原点的。
                    transOffsetX = (e.getX() - (float) getWidth() / 2) - (e.getX() - (float) getWidth() / 2) * bigScale / smallScale;
                    transOffsetY = (e.getY() - (float) getHeight() / 2) - (e.getY() - (float) getHeight() / 2) * bigScale / smallScale;
                    //修正偏移量，不超过边界
                    fixOffsets();
                    //双击放大之后，缩放指数也设为最大
                    totalScale = bigScale;
                    getScaleAnimator().start();
                } else {
                    //双击缩小之后，重置双指缩放
                    totalScale = smallScale;
                    getScaleAnimator().reverse();
                }
                return false;
            }

            /**
             * 早期谷歌地图，双击不抬起滑动，设置3维变化
             * 1、也就是双击同样被调用，区别就是双击后不抬起的话，之后的触摸事件move等等仍然回调给这个方法，直到up事件。
             *
             * @param e
             * @return 没用
             */
            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return false;
            }
        };

        /**
         * 普通手势监听回调
         */
        mGestureListener = new GestureDetector.OnGestureListener() {
            /**
             * 确定是否消费事件
             *
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
             *
             * @param e
             */
            @Override
            public void onShowPress(MotionEvent e) {

            }

            /**
             * 按下抬起，单机，<p>
             * 1、默认长按开启的话，超过500ms再抬起就不会收到回调了。
             * 2、如果设置关闭长按，那么就没有500ms限制了，随时抬起都会有回调。，mDetectorCompat.setIsLongpressEnabled(false);
             *
             * @param e
             * @return 返回值没有用
             */
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return false;
            }

            /**
             * 手指滑动
             *
             * @param down      按下时
             * @param event     移动后
             * @param distanceX 这个x偏移是：lastEvent.x - nowEvent.x = distanceX
             * @param distanceY 同上 也就是他是 起始点 - 终点 所以transOffsetX才用的 -=
             * @return
             */
            @Override
            public boolean onScroll(MotionEvent down, MotionEvent event, float distanceX, float distanceY) {
                //只有放大时可拖动
                if (isBig) {
                    transOffsetX -= distanceX;

                    transOffsetY -= distanceY;

                    //修正偏移量，不超过图片边界
                    fixOffsets();
                    //            Log.e("wzzzzzzzz", "onScroll: " + distanceX + "   " + distanceY + "  " + transOffsetY + "   " + transOffsetY);

                    invalidate();
                }
                return false;
            }

            /**
             * 长按回调，500ms
             *
             * @param e
             */
            @Override
            public void onLongPress(MotionEvent e) {

            }

            /**
             * 惯性滑动
             *
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
        mFlingRunnable = new Runnable() {
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
    }


    ///////////////////////////////////////////////////////////////////////////
    // 双指缩放 start
    ///////////////////////////////////////////////////////////////////////////
    {
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

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (scaleFraction != 0) {
                    isBig = true;
                } else {
                    isBig = false;
                }
            }

        };
    }

    float totalScale;

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
        //给双指缩放设置初始值
        totalScale = smallScale;
    }

    /**
     * 绘制
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //手指拖动，乘以*scaleFraction用来避免放大后，拖动之后再缩小不居中了就。
        canvas.translate(transOffsetX * scaleFraction, transOffsetY * scaleFraction);
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
        if (scaleFraction != 0) {
            scaleAnimator.setFloatValues(0, scaleFraction);
        } else {
            scaleAnimator.setFloatValues(0, 1);
        }
        return scaleAnimator;
    }

    /**
     * 修正偏移量，不超过边界
     */
    private void fixOffsets() {
        //设置不能拖出屏幕
        //x的最大值
        transOffsetX = Math.min(transOffsetX, (bitmap.getWidth() * bigScale - (float) getWidth()) / 2);
        //x的最小值
        transOffsetX = Math.max(transOffsetX, -(bitmap.getWidth() * bigScale - (float) getWidth()) / 2);
        transOffsetY = Math.min(transOffsetY, (bitmap.getHeight() * bigScale - (float) getHeight()) / 2);
        transOffsetY = Math.max(transOffsetY, -(bitmap.getHeight() * bigScale - (float) getHeight()) / 2);
    }

    public float getScaleFraction() {
        return scaleFraction;
    }

    public void setScaleFraction(float scaleFraction) {
        invalidate();
        this.scaleFraction = scaleFraction;
    }

}
