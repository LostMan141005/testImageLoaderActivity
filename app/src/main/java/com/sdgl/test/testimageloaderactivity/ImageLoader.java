package com.sdgl.test.testimageloaderactivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.widget.ImageView;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.LruMemoryCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.nostra13.universalimageloader.core.download.BaseImageDownloader;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.utils.StorageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * UniversalImageLoader 异步加载大量图片
 */
public class ImageLoader {

    private Context context;
    private int errorImage;
    private int loadingImage;
    private Map<String, Bitmap> cacheMap = new HashMap<>();

    public ImageLoader(Context context, int errorImage, int loadingImage) {
        this.context = context;
        this.errorImage = errorImage;
        this.loadingImage = loadingImage;
    }

    /**
     * 对外接口暴露，利用imageLoader的开源库效率比较高推荐使用
     * @return
     */
    public  com.nostra13.universalimageloader.core.ImageLoader getImageLoader() {
        File cacheDir = StorageUtils.getOwnCacheDirectory(context, "imageLoader/Cache");
        ImageLoaderConfiguration configuration = new ImageLoaderConfiguration.Builder(context)
                // default = device screen dimensions 内存缓存文件的最大长宽
                .memoryCacheExtraOptions(320, 480)
                //线程池内加载的数量
                .threadPoolSize(5)
                // default 设置当前线程的优先级
                //降低线程的优先级保证主UI线程不受太大影响
                .threadPriority(Thread.NORM_PRIORITY - 2)
                .denyCacheImageMultipleSizesInMemory()
                //可以通过自己的内存缓存实现
                //建议内存设在5-10M,可以有比较好的表现
                .memoryCache(new LruMemoryCache(10 * 1024 * 1024))
                // 内存缓存的最大值
                .memoryCacheSize(5 * 1024 * 1024)
                //100 Mb sd卡(本地)缓存的最大值
                .diskCacheSize(200 * 1024 * 1024)
                .diskCache(new UnlimitedDiskCache(cacheDir))
                // default为使用HASHCODE对UIL进行加密命名， 还可以用MD5(new Md5FileNameGenerator())加密
                .diskCacheFileNameGenerator(new Md5FileNameGenerator())
                .tasksProcessingOrder(QueueProcessingType.LIFO)
                .defaultDisplayImageOptions(getOptions())
//                .writeDebugLogs() //正常发版后这句话删除，作用是打出日志
                .imageDownloader(new BaseImageDownloader(context, 5 * 1000, 30 * 1000)) // connectTimeout (5 s), readTimeout (30 s)
                .build();
        com.nostra13.universalimageloader.core.ImageLoader imageLoader = com.nostra13.universalimageloader.core.ImageLoader.getInstance();
        imageLoader.init(configuration);
        return imageLoader;
    }

    /**
     * 获得imageLoader的相关参数
     * @return
     */
    public DisplayImageOptions getOptions() {
        //显示图片正在下载时候显示图片
        return new DisplayImageOptions.Builder().showImageOnLoading(R.drawable.loading)
                // 设置图片Uri为空或是错误的时候显示的图片
                .showImageForEmptyUri(R.drawable.error)
                // 设置图片加载或解码过程中发生错误显示的图片
                .showImageOnFail(R.drawable.error)
                // default  设置下载的图片是否缓存在内存中
                .cacheInMemory(true)
                // default  设置下载的图片是否缓存在SD卡中
                .cacheOnDisk(false)
                // default 设置图片以如何的编码方式显示
                .imageScaleType(ImageScaleType.IN_SAMPLE_INT)
                // default 设置图片的解码类型
                //设置为RGB565比起默认的ARGB_8888要节省大量的内存
                .bitmapConfig(Bitmap.Config.RGB_565)
                .resetViewBeforeLoading(true)
                .build();
    }

    public ImageLoader(Context context) {
        this(context, R.mipmap.blank, R.mipmap.blank);
    }


    /**
     * 通过第三方库来加载图片，第三方库提供了很好的内存管理
     * @param imageView
     * @param url
     */
    public void loadImage(ImageView imageView, String url) {
        ImageViewAware imageViewAware = new ImageViewAware(imageView, false);
        getImageLoader().clearMemoryCache();
        getImageLoader().displayImage(url, imageViewAware, getOptions());
    }


    /**
     * 自定义的图片根据url加载方案，根据三级缓存的原理
     * 第一级为运行内存，根据url获取bitmap时，url作为key将其缓存带内存中
     * 第二级缓存为SD卡，经请求中的最后文件名字原名存储到内存卡上，在请求文件时，根据url拆出文件名，让后再内存卡寻找
     * 第三级是在内存卡中没找到时在去联网请求图片，然后分别缓存到一、二级缓存
     * 在内存管理上不是太好，效率不够高
     * @param imageUrl
     * @param imageView
     */
    public void loadImage(String imageUrl, ImageView imageView) {
        if (imageUrl == null) {
            imageView.setImageResource(errorImage);
            return;
        }
        imageView.setTag(imageUrl);
        Bitmap bitmap;
        bitmap = getFromSecondCache(imageUrl);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
//            cacheMap.put(imageUrl, bitmap);
            return;
        }
        loadBitmapFromThirdCache(imageUrl, imageView);

    }

    private void loadBitmapFromThirdCache(final String imageUrl, final ImageView imageView) {
        final String[] filePath = new String[1];
        new AsyncTask<Void, Void, Bitmap>() {
            protected void onPreExecute() {
                imageView.setImageResource(loadingImage);
            }

            @Override
            protected Bitmap doInBackground(Void... params) {
                Bitmap bitmap = null;
                try {

                    String newImagePath = (String) imageView.getTag();
                    if (newImagePath != imageUrl) {
                        return null;
                    }
                    URL url = new URL(imageUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.connect();
                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200) {
                        InputStream is = connection.getInputStream();//图片文件流xutil的httputil没有找到请求的图片的对应方法，用原生的写的
                        bitmap = BitmapFactory.decodeStream(is);
                        is.close();
                        if (bitmap != null) {
                            String filesPath = context.getExternalFilesDir(null).getAbsolutePath();
                            String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);//  f10.jpg
                            filePath[0] = filesPath + "/" + fileName;
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(filePath[0]));
                        }
                    }
                    connection.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                bitmap = getSmallBitmap(filePath[0]);
//                cacheMap.put(imageUrl, bitmap);
                return bitmap;
            }

            protected void onPostExecute(Bitmap bitmap) {
                String newImagePath = (String) imageView.getTag();
                if (newImagePath != imageUrl) {
                    return;
                }
                if (bitmap == null) {
                    imageView.setImageResource(errorImage);
                } else {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }.execute();
    }

    private Bitmap getFromSecondCache(String imageUrl) {
        // /storage/sdcard/Android/data/packageName/files/
        String filesPath = context.getExternalFilesDir(null).getAbsolutePath();
        String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);//  f10.jpg
        String filePath = filesPath + "/" + fileName;
        return getSmallBitmap(filePath);
    }

    private Bitmap getFromFirstCache(String imageUrl) {
        return cacheMap.get(imageUrl);
    }


    public static Bitmap getSmallBitmap(String filePath) {

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, 480, 800);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        Bitmap bm = BitmapFactory.decodeFile(filePath, options);
        if(bm == null){
            return  null;
        }
        int degree = readPictureDegree(filePath);
        bm = rotateBitmap(bm,degree) ;
        ByteArrayOutputStream baos = null ;
        try{
            baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 30, baos);

        }finally{
            try {
                if(baos != null)
                    baos.close() ;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bm ;

    }

    public static Bitmap getSmallBitmap(InputStream inputStream) {

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, 480, 800);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        Bitmap bm = BitmapFactory.decodeStream(inputStream, new Rect(), options);
        if(bm == null){
            return  null;
        }
        int degree = 0;
        bm = rotateBitmap(bm,degree) ;
        ByteArrayOutputStream baos = null ;
        try{
            baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 30, baos);

        }finally{
            try {
                if(baos != null)
                    baos.close() ;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bm ;

    }


    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and
            // width
            final int heightRatio = Math.round((float) height
                    / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will
            // guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? widthRatio : heightRatio;
        }

        return inSampleSize;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotate){
        if(bitmap == null)
            return null ;

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Matrix mtx = new Matrix();
        mtx.postRotate(rotate);
        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    private static int readPictureDegree(String path) {
        int degree  = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }
}
