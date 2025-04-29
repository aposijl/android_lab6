package com.example.lab6;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MyAppWidget extends AppWidgetProvider {

    private static final String ACTION_WIDGET_REFRESH = "com.example.lab6.ACTION_WIDGET_REFRESH";
    private static final String ACTION_WIDGET_CLICKED = "com.example.lab6.ACTION_WIDGET_CLICKED";

    private static final String CAT_IMAGES_API_URL = "https://api.thecatapi.com/v1/images/search";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_WIDGET_REFRESH.equals(action)) {
            playMeowSound(context);

            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
            return;
        } else if (ACTION_WIDGET_CLICKED.equals(action)) {
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            return;
        }

        super.onReceive(context, intent);
    }

    private void playMeowSound(Context context) {
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.cat_meow);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                }
            });
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "не вдалося відтворити звук", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.my_app_widget);

        views.setTextViewText(R.id.widget_fact_text, "завантаження факту...");

        appWidgetManager.updateAppWidget(appWidgetId, views);

        Intent refreshIntent = new Intent(context, MyAppWidget.class);
        refreshIntent.setAction(ACTION_WIDGET_REFRESH);
        refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent);

        Intent clickIntent = new Intent(context, MyAppWidget.class);
        clickIntent.setAction(ACTION_WIDGET_CLICKED);
        PendingIntent clickPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId + 100, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_container, clickPendingIntent);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try {
                String catFact = getRandomCatFact();
                String imageUrl = fetchCatImageUrl();
                Bitmap catImage = null;

                if (imageUrl != null) {
                    catImage = downloadCatImage(imageUrl);
                }

                final String finalFact = catFact;
                final Bitmap finalImage = catImage;

                SharedPreferences sharedPreferences =
                        context.getSharedPreferences("CatFactsPrefs", Context.MODE_PRIVATE);
                Set<String> favoriteFacts =
                        sharedPreferences.getStringSet("favoriteFacts", new HashSet<>());
                final boolean isFavorite = favoriteFacts.contains(catFact);

                handler.post(() -> {
                    views.setTextViewText(R.id.widget_fact_text, finalFact);

                    if (isFavorite) {
                        views.setTextViewText(R.id.widget_title, "улюблений факт про котіка ❤️");
                    } else {
                        views.setTextViewText(R.id.widget_title, "факт про котіка");
                    }

                    if (finalImage != null) {
                        views.setImageViewBitmap(R.id.widget_cat_image, finalImage);
                    } else {
                        views.setImageViewResource(R.id.widget_cat_image, getRandomCatImage());
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });

            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    views.setTextViewText(R.id.widget_fact_text, getRandomCatFact());
                    views.setImageViewResource(R.id.widget_cat_image, getRandomCatImage());
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });
            }
        });
    }

    private String fetchCatImageUrl() {
        String imageUrl = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(CAT_IMAGES_API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONArray jsonArray = new JSONArray(response.toString());
                if (jsonArray.length() > 0) {
                    JSONObject jsonObject = jsonArray.getJSONObject(0);
                    imageUrl = jsonObject.getString("url");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return imageUrl;
    }

    private Bitmap downloadCatImage(String imageUrl) {
        Bitmap bitmap = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            InputStream inputStream = connection.getInputStream();
            bitmap = BitmapFactory.decodeStream(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return bitmap;
    }

    private String getRandomCatFact() {
        String[] catFacts = {
                "коти проводять 70% свого життя сплячими.",
                "коти не можуть відчути солодкий смак.",
                "нормальна температура тіла кота 38°C.",
                "коти мають 32 м'язи в кожному вусі.",
                "найстарішому домашньому коту було 38 років.",
                "коти не мають потових залоз по всьому тілу.",
                "мурчання кота має цілющі властивості.",
                "коти можуть обертати вухами на 180 градусів.",
                "кіт може стрибнути на висоту в 5 разів більшу за свій зріст.",
                "домашні коти можуть бігти зі швидкістю до 50 км/год на короткі дистанції.",
                "мозок кота більш схожий на мозок людини, ніж на мозок собаки.",
                "у котів є унікальні відбитки носа, як відбитки пальців у людей.",
                "кішки бачать у 6 разів краще людей у темряві.",
                "кішки можуть чути ультразвук.",
                "кішки здатні запам'ятати до 100 різних слів.",
                "кількість вусів у кота може змінюватися.",
                "багато котів є сповільненими лактозними.",
                "коти не можуть лазити вниз головою через структуру їхніх кігтів.",
                "домашні коти спілкуються з людьми інакше, ніж між собою."
        };

        int randomIndex = (int) (Math.random() * catFacts.length);
        return catFacts[randomIndex];
    }

    private int getRandomCatImage() {
        int[] catImages = {
                R.drawable.cat_1,
                R.drawable.cat_2,
                R.drawable.cat_3,
                R.drawable.cat_4,
                R.drawable.cat_5
        };

        int randomIndex = (int) (Math.random() * catImages.length);
        return catImages[randomIndex];
    }
}