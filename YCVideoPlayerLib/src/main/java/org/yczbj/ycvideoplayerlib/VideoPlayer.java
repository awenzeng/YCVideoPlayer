package org.yczbj.ycvideoplayerlib;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;


/**
 * @author yc
 * @date 2017/11/29
 * 参考项目：
 * https://github.com/CarGuo/GSYVideoPlayer
 * https://github.com/danylovolokh/VideoPlayerManager
 * https://github.com/HotBitmapGG/bilibili-android-client
 * https://github.com/jjdxmashl/jjdxm_ijkplayer
 * https://github.com/JasonChow1989/JieCaoVideoPlayer-develop          2年前
 * https://github.com/open-android/JieCaoVideoPlayer                   1年前
 * https://github.com/lipangit/JiaoZiVideoPlayer                       4个月前
 * 个人感觉jiaozi这个播放器，与JieCaoVideoPlayer-develop有惊人的类同，借鉴了上面两个项目[JieCao]
 *
 *
 * 注意：在对应的播放Activity页面，清单文件中一定要添加
 * android:configChanges="orientation|keyboardHidden|screenSize"
 * android:screenOrientation="portrait"
 *
 * 关于我的github：https://github.com/yangchong211
 * 关于我的个人网站：www.ycbjie.cn或者www.yczbj.org
 *
 * 播放器
 */

public class VideoPlayer extends FrameLayout implements InterVideoPlayer{

    /**
     * 播放错误
     **/
    public static final int STATE_ERROR = -1;
    /**
     * 播放未开始
     **/
    public static final int STATE_IDLE = 0;
    /**
     * 播放准备中
     **/
    public static final int STATE_PREPARING = 1;
    /**
     * 播放准备就绪
     **/
    public static final int STATE_PREPARED = 2;
    /**
     * 正在播放
     **/
    public static final int STATE_PLAYING = 3;
    /**
     * 暂停播放
     **/
    public static final int STATE_PAUSED = 4;
    /**
     * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，缓冲区数据足够后恢复播放)
     **/
    public static final int STATE_BUFFERING_PLAYING = 5;
    /**
     * 正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，此时暂停播放器，继续缓冲，缓冲区数据足够后恢复暂停
     **/
    public static final int STATE_BUFFERING_PAUSED = 6;
    /**
     * 播放完成
     **/
    public static final int STATE_COMPLETED = 7;

    /**
     * 普通模式
     **/
    public static final int MODE_NORMAL = 10;
    /**
     * 全屏模式
     **/
    public static final int MODE_FULL_SCREEN = 11;
    /**
     * 小窗口模式
     **/
    public static final int MODE_TINY_WINDOW = 12;

    /**
     * IjkPlayer，基于IjkPlayer封装播放器
     **/
    public static final int TYPE_IJK = 111;
    /**
     * MediaPlayer，基于原生自带的播放器控件
     **/
    public static final int TYPE_NATIVE = 222;
    /**
     * 播放类型
     * TYPE_IJK             基于IjkPlayer封装播放器
     * TYPE_NATIVE          基于原生自带的播放器控件
     **/
    private int mPlayerType = TYPE_IJK;
    /**
     * 播放状态，错误，开始播放，暂停播放，缓存中等等状态
     **/
    private int mCurrentState = STATE_IDLE;
    /**
     * 播放模式，普通模式，小窗口模式，正常模式等等
     **/
    private int mCurrentMode = MODE_NORMAL;


    private Context mContext;
    private AudioManager mAudioManager;
    private IMediaPlayer mMediaPlayer;
    private FrameLayout mContainer;
    private VideoTextureView mTextureView;
    private AbsVideoPlayerController mController;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private String mUrl;
    private Map<String, String> mHeaders;
    private int mBufferPercentage;
    private boolean continueFromLastPosition = true;
    private long skipToPosition;

    public VideoPlayer(Context context) {
        this(context, null);
    }

    public VideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    /**
     * 初始化
     */
    private void init() {
        mContainer = new FrameLayout(mContext);
        //设置背景颜色，目前设置为纯黑色
        mContainer.setBackgroundColor(Color.BLACK);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mContainer, params);
    }

    /*--------------setUp为必须设置的方法，二选其一--------------------------------------*/
    /**
     * 设置，必须设置
     * @param url               视频地址，可以是本地，也可以是网络视频
     * @param headers           请求header.
     */
    @Override
    public void setUp(String url, Map<String, String> headers) {
        mUrl = url;
        mHeaders = headers;
    }


    /**
     * 设置视频控制器，必须设置
     * @param controller        AbsVideoPlayerController子类对象，可用VideoPlayerController，也可自定义
     */
    public void setController(AbsVideoPlayerController controller) {
        //这里必须先移除
        mContainer.removeView(mController);
        mController = controller;
        mController.reset();
        mController.setVideoPlayer(this);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mContainer.addView(mController, params);
    }



    /**
     * 设置播放器类型，必须设置
     * @param playerType IjkPlayer or MediaPlayer.
     */
    public void setPlayerType(int playerType) {
        mPlayerType = playerType;
    }


    /**
     * 是否从上一次的位置继续播放，不必须
     *
     * @param continueFromLastPosition true从上一次的位置继续播放
     */
    @Override
    public void continueFromLastPosition(boolean continueFromLastPosition) {
        this.continueFromLastPosition = continueFromLastPosition;
    }


    /**
     * 注意：MediaPlayer没有这个方法
     * 设置播放速度，不必须
     * @param speed                     播放速度
     */
    @Override
    public void setSpeed(float speed) {
        if (mMediaPlayer instanceof IjkMediaPlayer) {
            ((IjkMediaPlayer) mMediaPlayer).setSpeed(speed);
        } else if(mMediaPlayer instanceof MediaPlayer){
            //((MediaPlayer) mMediaPlayer).setSpeed(speed);
            VideoLogUtil.d("只有IjkPlayer才能设置播放速度");
        } else {
            VideoLogUtil.d("只有IjkPlayer才能设置播放速度");
        }
    }


    /**
     * 开始播放
     */
    @Override
    public void start() {
        if (mCurrentState == STATE_IDLE) {
            VideoPlayerManager.instance().setCurrentVideoPlayer(this);
            initAudioManager();
            initMediaPlayer();
            initTextureView();
            addTextureView();
        } else {
            VideoLogUtil.d("VideoPlayer只有在mCurrentState == STATE_IDLE时才能调用start方法.");
        }
    }


    /**
     * 开始播放
     * @param position                 播放位置
     */
    @Override
    public void start(long position) {
        skipToPosition = position;
        start();
    }


    /**
     * 重新播放
     */
    @Override
    public void restart() {
        if (mCurrentState == STATE_PAUSED) {
            //如果是暂停状态，那么则继续播放
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("STATE_PLAYING");
        } else if (mCurrentState == STATE_BUFFERING_PAUSED) {
            //如果是缓存暂停状态，那么则继续播放
            mMediaPlayer.start();
            mCurrentState = STATE_BUFFERING_PLAYING;
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("STATE_BUFFERING_PLAYING");
        } else if (mCurrentState == STATE_COMPLETED || mCurrentState == STATE_ERROR) {
            //如果是完成播放或者播放错误，则重新播放
            mMediaPlayer.reset();
            openMediaPlayer();
        } else {
            VideoLogUtil.d("VideoPlayer在mCurrentState == " + mCurrentState + "时不能调用restart()方法.");
        }
    }


    /**
     * 暂停播放
     */
    @Override
    public void pause() {
        if (mCurrentState == STATE_PLAYING) {
            mMediaPlayer.pause();
            mCurrentState = STATE_PAUSED;
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("STATE_PAUSED");
        }
        if (mCurrentState == STATE_BUFFERING_PLAYING) {
            mMediaPlayer.pause();
            mCurrentState = STATE_BUFFERING_PAUSED;
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("STATE_BUFFERING_PAUSED");
        }
    }


    /**
     * 设置播放位置
     * @param pos                   播放位置
     */
    @Override
    public void seekTo(long pos) {
        if (mMediaPlayer != null) {
            mMediaPlayer.seekTo(pos);
        }
    }


    /**
     * 设置音量
     * @param volume                音量值
     */
    @Override
    public void setVolume(int volume) {
        if (mAudioManager != null) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
        }
    }


    /**
     * 判断是否开始播放
     * @return                      true表示播放未开始
     */
    @Override
    public boolean isIdle() {
        return mCurrentState == STATE_IDLE;
    }


    /**
     * 判断视频是否播放准备中
     * @return                      true表示播放准备中
     */
    @Override
    public boolean isPreparing() {
        return mCurrentState == STATE_PREPARING;
    }


    /**
     * 判断视频是否准备就绪
     * @return                      true表示播放准备就绪
     */
    @Override
    public boolean isPrepared() {
        return mCurrentState == STATE_PREPARED;
    }


    /**
     * 判断视频是否正在缓冲(播放器正在播放时，缓冲区数据不足，进行缓冲，缓冲区数据足够后恢复播放)
     * @return                      true表示正在缓冲
     */
    @Override
    public boolean isBufferingPlaying() {
        return mCurrentState == STATE_BUFFERING_PLAYING;
    }


    /**
     * 判断是否是否缓冲暂停
     * @return                      true表示缓冲暂停
     */
    @Override
    public boolean isBufferingPaused() {
        return mCurrentState == STATE_BUFFERING_PAUSED;
    }


    /**
     * 判断视频是否正在播放
     * @return                      true表示正在播放
     */
    @Override
    public boolean isPlaying() {
        return mCurrentState == STATE_PLAYING;
    }


    /**
     * 判断视频是否暂停播放
     * @return                      true表示暂停播放
     */
    @Override
    public boolean isPaused() {
        return mCurrentState == STATE_PAUSED;
    }


    /**
     * 判断视频是否播放错误
     * @return                      true表示播放错误
     */
    @Override
    public boolean isError() {
        return mCurrentState == STATE_ERROR;
    }


    /**
     * 判断视频是否播放完成
     * @return                      true表示播放完成
     */
    @Override
    public boolean isCompleted() {
        return mCurrentState == STATE_COMPLETED;
    }


    /**
     * 判断视频是否播放全屏
     * @return                      true表示播放全屏
     */
    @Override
    public boolean isFullScreen() {
        return mCurrentMode == MODE_FULL_SCREEN;
    }


    /**
     * 判断视频是否播放小窗口
     * @return                      true表示播放小窗口
     */
    @Override
    public boolean isTinyWindow() {
        return mCurrentMode == MODE_TINY_WINDOW;
    }


    /**
     * 判断视频是否正常播放
     * @return                      true表示正常播放
     */
    @Override
    public boolean isNormal() {
        return mCurrentMode == MODE_NORMAL;
    }


    /**
     * 获取最大音量
     * @return                  音量值
     */
    @Override
    public int getMaxVolume() {
        if (mAudioManager != null) {
            return mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        return 0;
    }


    /**
     * 获取音量值
     * @return                  音量值
     */
    @Override
    public int getVolume() {
        if (mAudioManager != null) {
            return mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        return 0;
    }


    /**
     * 获取持续时长
     * @return                  long时间值
     */
    @Override
    public long getDuration() {
        return mMediaPlayer != null ? mMediaPlayer.getDuration() : 0;
    }


    /**
     * 获取播放位置
     * @return                  位置
     */
    @Override
    public long getCurrentPosition() {
        return mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0;
    }


    /**
     * 获取缓冲区百分比
     * @return                  百分比
     */
    @Override
    public int getBufferPercentage() {
        return mBufferPercentage;
    }


    /**
     * 获取播放速度
     * @param speed             播放速度
     * @return                  速度
     */
    @Override
    public float getSpeed(float speed) {
        if (mMediaPlayer instanceof IjkMediaPlayer) {
            return ((IjkMediaPlayer) mMediaPlayer).getSpeed(speed);
        }
        return 0;
    }


    /**
     * 获取播放速度
     * @return                  速度
     */
    @Override
    public long getTcpSpeed() {
        if (mMediaPlayer instanceof IjkMediaPlayer) {
            return ((IjkMediaPlayer) mMediaPlayer).getTcpSpeed();
        }
        return 0;
    }


    /**
     * 初始化音频管理器
     */
    @RequiresApi(api = Build.VERSION_CODES.FROYO)
    private void initAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            if (mAudioManager != null) {
                mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        }
    }


    /**
     * 初始化视频管理器
     */
    private void initMediaPlayer() {
        if (mMediaPlayer == null) {
            switch (mPlayerType) {
                //MediaPlayer       基于原生
                case TYPE_NATIVE:
                    mMediaPlayer = new AndroidMediaPlayer();
                    break;
                //IjkMediaPlayer    基于Ijk
                case TYPE_IJK:
                default:
                    //创建IjkMediaPlayer对象
                    mMediaPlayer = new IjkMediaPlayer();
                    //设置ijkplayer播放器的硬件解码相关参数
                    ((IjkMediaPlayer)mMediaPlayer).setOption(1, "analyzemaxduration", 100L);
                    ((IjkMediaPlayer)mMediaPlayer).setOption(1, "probesize", 10240L);
                    ((IjkMediaPlayer)mMediaPlayer).setOption(1, "flush_packets", 1L);
                    ((IjkMediaPlayer)mMediaPlayer).setOption(4, "packet-buffering", 0L);
                    ((IjkMediaPlayer)mMediaPlayer).setOption(4, "framedrop", 1L);
                    break;
            }
            //设置音频流类型
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }
    }


    /**
     * 初始化TextureView
     */
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void initTextureView() {
        if (mTextureView == null) {
            mTextureView = new VideoTextureView(mContext);
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

                /**
                 * SurfaceTexture准备就绪
                 */
                @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if (mSurfaceTexture == null) {
                        mSurfaceTexture = surface;
                        openMediaPlayer();
                    } else {
                        mTextureView.setSurfaceTexture(mSurfaceTexture);
                    }
                }

                /**
                 * SurfaceTexture缓冲大小变化
                 */
                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                }

                /**
                 * SurfaceTexture即将被销毁
                 */
                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return mSurfaceTexture == null;
                }

                /**
                 * SurfaceTexture通过updateImage更新
                 */
                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
        }
    }


    /**
     * 添加TextureView到视图中
     */
    private void addTextureView() {
        mContainer.removeView(mTextureView);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        mContainer.addView(mTextureView, 0, params);
    }


    /**
     * 打开MediaPlayer播放器
     */
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void openMediaPlayer() {
        // 屏幕常亮
        mContainer.setKeepScreenOn(true);
        // 设置监听，可以查看ijk中的IMediaPlayer源码监听事件
        // 设置准备视频播放监听事件
        mMediaPlayer.setOnPreparedListener(mOnPreparedListener);
        // 设置视频播放完成监听事件
        mMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        // 设置视频缓冲更新监听事件
        mMediaPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        // 设置视频seek完成监听事件
        mMediaPlayer.setOnSeekCompleteListener(mOnSeekCompleteListener);
        // 设置视频大小更改监听器
        mMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
        // 设置视频错误监听器
        mMediaPlayer.setOnErrorListener(mOnErrorListener);
        // 设置视频信息监听器
        mMediaPlayer.setOnInfoListener(mOnInfoListener);
        // 设置时间文本监听器
        mMediaPlayer.setOnTimedTextListener(mOnTimedTextListener);
        // 设置dataSource
        try {
            mMediaPlayer.setDataSource(mContext.getApplicationContext(), Uri.parse(mUrl), mHeaders);
            if (mSurface == null) {
                mSurface = new Surface(mSurfaceTexture);
            }
            //
            mMediaPlayer.setSurface(mSurface);
            // 开始加载
            mMediaPlayer.prepareAsync();
            // 播放准备中
            mCurrentState = STATE_PREPARING;
            // 控制器，更新不同的播放状态的UI
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("STATE_PREPARING");
        } catch (IOException e) {
            e.printStackTrace();
            VideoLogUtil.e("打开播放器发生错误", e);
        }
    }


    /**
     * 设置准备视频播放监听事件
     */
    private IMediaPlayer.OnPreparedListener mOnPreparedListener = new IMediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(IMediaPlayer mp) {
            mCurrentState = STATE_PREPARED;
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("onPrepared ——> STATE_PREPARED");
            mp.start();
            // 从上次的保存位置播放
            if (continueFromLastPosition) {
                long savedPlayPosition = VideoPlayerUtils.getSavedPlayPosition(mContext, mUrl);
                mp.seekTo(savedPlayPosition);
            }
            // 跳到指定位置播放
            if (skipToPosition != 0) {
                mp.seekTo(skipToPosition);
            }
        }
    };


    /**
     * 设置视频播放完成监听事件
     */
    private IMediaPlayer.OnCompletionListener mOnCompletionListener = new IMediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(IMediaPlayer mp) {
            mCurrentState = STATE_COMPLETED;
            mController.onPlayStateChanged(mCurrentState);
            VideoLogUtil.d("onCompletion ——> STATE_COMPLETED");
            // 清除屏幕常亮
            mContainer.setKeepScreenOn(false);
        }
    };

    /**
     * 设置视频缓冲更新监听事件
     */
    private IMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = new IMediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(IMediaPlayer mp, int percent) {
            mBufferPercentage = percent;
        }
    };

    /**
     * 设置视频seek完成监听事件
     */
    private IMediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener = new IMediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(IMediaPlayer iMediaPlayer) {

        }
    };

    /**
     * 设置视频大小更改监听器
     */
    private IMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener = new IMediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
            mTextureView.adaptVideoSize(width, height);
            VideoLogUtil.d("onVideoSizeChanged ——> width：" + width + "， height：" + height);
        }
    };

    /**
     * 设置视频错误监听器
     * int MEDIA_INFO_VIDEO_RENDERING_START = 3;//视频准备渲染
     * int MEDIA_INFO_BUFFERING_START = 701;//开始缓冲
     * int MEDIA_INFO_BUFFERING_END = 702;//缓冲结束
     * int MEDIA_INFO_VIDEO_ROTATION_CHANGED = 10001;//视频选择信息
     * int MEDIA_ERROR_SERVER_DIED = 100;//视频中断，一般是视频源异常或者不支持的视频类型。
     * int MEDIA_ERROR_IJK_PLAYER = -10000,//一般是视频源有问题或者数据格式不支持，比如音频不是AAC之类的
     * int MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK = 200;//数据错误没有有效的回收
     */
    private IMediaPlayer.OnErrorListener mOnErrorListener = new IMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(IMediaPlayer mp, int what, int extra) {
            // 直播流播放时去调用mediaPlayer.getDuration会导致-38和-2147483648错误，忽略该错误
            if (what != -38 && what != -2147483648 && extra != -38 && extra != -2147483648) {
                mCurrentState = STATE_ERROR;
                mController.onPlayStateChanged(mCurrentState);
                VideoLogUtil.d("onError ——> STATE_ERROR ———— what：" + what + ", extra: " + extra);
            }
            return true;
        }
    };

    /**
     * 设置视频信息监听器
     */
    private IMediaPlayer.OnInfoListener mOnInfoListener = new IMediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer mp, int what, int extra) {
            if (what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                // 播放器开始渲染
                mCurrentState = STATE_PLAYING;
                mController.onPlayStateChanged(mCurrentState);
                VideoLogUtil.d("onInfo ——> MEDIA_INFO_VIDEO_RENDERING_START：STATE_PLAYING");
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                // MediaPlayer暂时不播放，以缓冲更多的数据
                if (mCurrentState == STATE_PAUSED || mCurrentState == STATE_BUFFERING_PAUSED) {
                    mCurrentState = STATE_BUFFERING_PAUSED;
                    VideoLogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PAUSED");
                } else {
                    mCurrentState = STATE_BUFFERING_PLAYING;
                    VideoLogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_START：STATE_BUFFERING_PLAYING");
                }
                mController.onPlayStateChanged(mCurrentState);
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                // 填充缓冲区后，MediaPlayer恢复播放/暂停
                if (mCurrentState == STATE_BUFFERING_PLAYING) {
                    mCurrentState = STATE_PLAYING;
                    mController.onPlayStateChanged(mCurrentState);
                    VideoLogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PLAYING");
                }
                if (mCurrentState == STATE_BUFFERING_PAUSED) {
                    mCurrentState = STATE_PAUSED;
                    mController.onPlayStateChanged(mCurrentState);
                    VideoLogUtil.d("onInfo ——> MEDIA_INFO_BUFFERING_END： STATE_PAUSED");
                }
            } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
                // 视频旋转了extra度，需要恢复
                if (mTextureView != null) {
                    mTextureView.setRotation(extra);
                    VideoLogUtil.d("视频旋转角度：" + extra);
                }
            } else if (what == IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE) {
                VideoLogUtil.d("视频不能seekTo，为直播视频");
            } else {
                VideoLogUtil.d("onInfo ——> what：" + what);
            }
            return true;
        }
    };


    /**
     * 设置时间文本监听器
     */
    private IMediaPlayer.OnTimedTextListener mOnTimedTextListener = new IMediaPlayer.OnTimedTextListener() {
        @Override
        public void onTimedText(IMediaPlayer iMediaPlayer, IjkTimedText ijkTimedText) {

        }
    };


    /**
     * 进入全屏模式
     * 全屏，将mContainer(内部包含mTextureView和mController)从当前容器中移除，并添加到android.R.content中.
     * 切换横屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期
     */
    @Override
    public void enterFullScreen() {
        if (mCurrentMode == MODE_FULL_SCREEN){
            return;
        }
        // 隐藏ActionBar、状态栏，并横屏
        VideoPlayerUtils.hideActionBar(mContext);
        VideoPlayerUtils.scanForActivity(mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ViewGroup contentView = (ViewGroup) VideoPlayerUtils.scanForActivity(mContext).findViewById(android.R.id.content);
        if (mCurrentMode == MODE_TINY_WINDOW) {
            contentView.removeView(mContainer);
        } else {
            this.removeView(mContainer);
        }
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentView.addView(mContainer, params);

        mCurrentMode = MODE_FULL_SCREEN;
        mController.onPlayModeChanged(mCurrentMode);
        VideoLogUtil.d("MODE_FULL_SCREEN");
    }


    /**
     * 进入竖屏的全屏模式
     */
    @Override
    public void enterVerticalScreenScreen() {
        if (mCurrentMode == MODE_FULL_SCREEN){
            return;
        }
        // 隐藏ActionBar、状态栏，并横屏
        VideoPlayerUtils.hideActionBar(mContext);
        VideoPlayerUtils.scanForActivity(mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ViewGroup contentView = (ViewGroup) VideoPlayerUtils.scanForActivity(mContext).findViewById(android.R.id.content);
        if (mCurrentMode == MODE_TINY_WINDOW) {
            contentView.removeView(mContainer);
        } else {
            this.removeView(mContainer);
        }
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentView.addView(mContainer, params);

        mCurrentMode = MODE_FULL_SCREEN;
        mController.onPlayModeChanged(mCurrentMode);
        VideoLogUtil.d("MODE_FULL_SCREEN");
    }




    /**
     * 退出全屏模式
     * 退出全屏，移除mTextureView和mController，并添加到非全屏的容器中。
     * 切换竖屏时需要在manifest的activity标签下添加android:configChanges="orientation|keyboardHidden|screenSize"配置，
     * 以避免Activity重新走生命周期.
     *
     * @return true退出全屏.
     */
    @Override
    public boolean exitFullScreen() {
        if (mCurrentMode == MODE_FULL_SCREEN) {
            VideoPlayerUtils.showActionBar(mContext);
            VideoPlayerUtils.scanForActivity(mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            ViewGroup contentView = (ViewGroup) VideoPlayerUtils.scanForActivity(mContext).findViewById(android.R.id.content);
            //将视图移除
            contentView.removeView(mContainer);
            //重新添加到当前视图
            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            this.addView(mContainer, params);
            mCurrentMode = MODE_NORMAL;
            mController.onPlayModeChanged(mCurrentMode);
            VideoLogUtil.d("MODE_NORMAL");
            return true;
        }
        return false;
    }


    /**
     * 进入小窗口播放，小窗口播放的实现原理与全屏播放类似。
     */
    @Override
    public void enterTinyWindow() {
        //如果是小窗口模式，则不执行下面代码
        if (mCurrentMode == MODE_TINY_WINDOW) {
            return;
        }
        this.removeView(mContainer);
        ViewGroup contentView = (ViewGroup) VideoPlayerUtils.scanForActivity(mContext)
                .findViewById(android.R.id.content);
        // 小窗口的宽度为屏幕宽度的60%，长宽比默认为16:9，右边距、下边距为8dp。
        LayoutParams params = new LayoutParams(
                (int) (VideoPlayerUtils.getScreenWidth(mContext) * 0.6f),
                (int) (VideoPlayerUtils.getScreenWidth(mContext) * 0.6f * 9f / 16f));
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.rightMargin = VideoPlayerUtils.dp2px(mContext, 8f);
        params.bottomMargin = VideoPlayerUtils.dp2px(mContext, 8f);

        contentView.addView(mContainer, params);

        mCurrentMode = MODE_TINY_WINDOW;
        mController.onPlayModeChanged(mCurrentMode);
        VideoLogUtil.d("MODE_TINY_WINDOW");
    }

    /**
     * 退出小窗口播放
     */
    @Override
    public boolean exitTinyWindow() {
        if (mCurrentMode == MODE_TINY_WINDOW) {
            ViewGroup contentView = (ViewGroup) VideoPlayerUtils.scanForActivity(mContext).findViewById(android.R.id.content);
            contentView.removeView(mContainer);
            LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            this.addView(mContainer, params);
            mCurrentMode = MODE_NORMAL;
            mController.onPlayModeChanged(mCurrentMode);
            VideoLogUtil.d("MODE_NORMAL");
            return true;
        }
        return false;
    }


    /**
     * 释放，内部的播放器被释放掉，同时如果在全屏、小窗口模式下都会退出
     * 逻辑
     * 1.先保存播放位置
     * 2.退出全屏或小窗口，回复播放模式为正常模式
     * 3.释放播放器
     * 4.恢复控制器
     * 5.gc回收
     */
    @Override
    public void release() {
        // 保存播放位置，当正在播放时，缓冲时，缓冲暂停时，暂停时
        if (isPlaying() || isBufferingPlaying() || isBufferingPaused() || isPaused()) {
            VideoPlayerUtils.savePlayPosition(mContext, mUrl, getCurrentPosition());
        } else if (isCompleted()) {
            //如果播放完成，则保存播放位置为0，也就是初始位置
            VideoPlayerUtils.savePlayPosition(mContext, mUrl, 0);
        }
        // 退出全屏或小窗口
        if (isFullScreen()) {
            exitFullScreen();
        }
        if (isTinyWindow()) {
            exitTinyWindow();
        }
        mCurrentMode = MODE_NORMAL;

        // 释放播放器
        releasePlayer();

        // 恢复控制器
        if (mController != null) {
            mController.reset();
        }
        // gc回收
        Runtime.getRuntime().gc();
    }

    /**
     * 释放播放器，注意一定要判断对象是否为空，增强严谨性
     * 这样以便在当前播放器状态下可以方便的切换不同的清晰度的视频地址
     * 关于我的github：https://github.com/yangchong211
     * 关于我的个人网站：www.ycbjie.cn或者www.yczbj.org
     * 杨充修改：
     *      17年12月23日，添加释放音频和TextureView
     */
    @Override
    public void releasePlayer() {
        if (mAudioManager != null) {
            //放弃音频焦点。使以前的焦点所有者(如果有的话)接收焦点。
            mAudioManager.abandonAudioFocus(null);
            //置空
            mAudioManager = null;
        }
        if (mMediaPlayer != null) {
            //释放视频焦点
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        //从视图中移除TextureView
        mContainer.removeView(mTextureView);
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        //如果SurfaceTexture不为null，则释放
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        mCurrentState = STATE_IDLE;
    }


}
