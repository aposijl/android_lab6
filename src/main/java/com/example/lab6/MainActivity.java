package com.example.lab6;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.view.View;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.Toast;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String CAT_FACTS_API_URL = "https://catfact.ninja/fact";
    private static final String MEOW_FACTS_API_URL = "https://meowfacts.herokuapp.com/";
    private static final String CAT_IMAGES_API_URL = "https://api.thecatapi.com/v1/images/search";

    private TextView factTextView;
    private ImageView catImageView;
    private TextView factCounterTextView;
    private ImageButton favoriteButton;
    private TextView sourceIndicator;
    private ProgressBar progressBar;
    private ProgressBar imageProgressBar;
    private int factCounter = 0;
    private Random random = new Random();

    private SharedPreferences sharedPreferences;
    private Set<String> favoriteFacts;
    private String currentFact;

    private ExecutorService executor;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());

        factTextView = findViewById(R.id.fact_text_view);
        catImageView = findViewById(R.id.cat_image_view);
        factCounterTextView = findViewById(R.id.fact_counter);
        progressBar = findViewById(R.id.progress_bar);
        imageProgressBar = findViewById(R.id.image_progress_bar);
        sourceIndicator = findViewById(R.id.source_indicator);

        Button newFactButton = findViewById(R.id.new_fact_button);
        Button instructionsButton = findViewById(R.id.instructions_button);
        Button aboutButton = findViewById(R.id.about_button);

        favoriteButton = findViewById(R.id.favorite_button);
        sharedPreferences = getSharedPreferences("CatFactsPrefs", MODE_PRIVATE);
        favoriteFacts = new HashSet<>(sharedPreferences.getStringSet("favoriteFacts", new HashSet<>()));

        favoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFavorite();
            }
        });

        Button favoritesListButton = findViewById(R.id.favorites_list_button);
        favoritesListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFavoritesList();
            }
        });

        showRandomFact();

        newFactButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRandomFact();
            }
        });

        instructionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstructions();
            }
        });

        aboutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAboutDialog();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }

    private void showRandomFact() {
        progressBar.setVisibility(View.VISIBLE);
        imageProgressBar.setVisibility(View.VISIBLE);
        factTextView.setVisibility(View.INVISIBLE);
        catImageView.setVisibility(View.INVISIBLE);
        sourceIndicator.setText("завантаження...");
        sourceIndicator.setTextColor(getResources().getColor(android.R.color.darker_gray));

        executor.execute(() -> {
            String fact = fetchCatFactFromAPI();
            boolean isFromApi = true;
            boolean isImageFromApi = false;
            Bitmap catImage = null;
            int localImageResId = getRandomCatImage();

            if (fact == null || fact.isEmpty()) {
                fact = getLocalRandomCatFact();
                isFromApi = false;
            }

            String imageUrl = fetchCatImageUrl();
            if (imageUrl != null) {
                catImage = downloadCatImage(imageUrl);
                if (catImage != null) {
                    isImageFromApi = true;
                }
            }

            final String finalFact = fact;
            final boolean finalIsFromApi = isFromApi;
            final boolean finalIsImageFromApi = isImageFromApi;
            final Bitmap finalCatImage = catImage;
            final int finalLocalImageResId = localImageResId;

            handler.post(() -> {
                progressBar.setVisibility(View.GONE);
                imageProgressBar.setVisibility(View.GONE);
                factTextView.setVisibility(View.VISIBLE);
                catImageView.setVisibility(View.VISIBLE);

                currentFact = finalFact;
                factTextView.setText(currentFact);

                if (finalIsImageFromApi && finalCatImage != null) {
                    catImageView.setImageBitmap(finalCatImage);
                } else {
                    catImageView.setImageResource(finalLocalImageResId);
                }

                if (finalIsFromApi) {
                    if (finalIsImageFromApi) {
                        sourceIndicator.setText("онлайн (факт + фото)");
                    } else {
                        sourceIndicator.setText("онлайн (тільки факт)");
                    }
                    sourceIndicator.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                } else {
                    if (finalIsImageFromApi) {
                        sourceIndicator.setText("частково онлайн (тільки фото)");
                        sourceIndicator.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                    } else {
                        sourceIndicator.setText("офлайн");
                        sourceIndicator.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
                    }
                }

                factCounter++;
                factCounterTextView.setText(getString(R.string.facts_viewed, factCounter));

                updateFavoriteButtonState();
            });
        });
    }

    private void showInstructions() {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("як використовувати віджет")
                .setMessage("1. натисніть і утримуйте пальцем на вільному місці головного екрану\n\n" +
                        "2. виберіть 'Віджети' з меню\n\n" +
                        "3. знайдіть і перетягніть віджет 'lab6' на головний екран\n\n" +
                        "4. натисніть на кнопку оновлення для нового факту про котиків\n\n" +
                        "5. натисніть на сам віджет, щоб відкрити додаток")
                .setPositiveButton("зрозуміло", null)
                .show();
    }

    private void showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("про додаток")
                .setMessage("додаток 'lab6' v1.0\n\n" +
                        "цей додаток показує цікаві факти про кітиків та їхні фото.\n\n" +
                        "розробник: Бадюлько Іра\n\n" +
                        "джерела фактів: API catfact.ninja та meowfacts.herokuapp.com\n" +
                        "джерела зображень: API thecatapi.com та внутрішні ресурси")
                .setPositiveButton("відвідати сайт", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://thecatapi.com"));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("закрити", null)
                .show();
    }

    private void toggleFavorite() {
        Set<String> updatedFavorites = new HashSet<>(favoriteFacts);

        if (updatedFavorites.contains(currentFact)) {
            updatedFavorites.remove(currentFact);
            Toast.makeText(this, "факт видалено з улюблених", Toast.LENGTH_SHORT).show();
        } else {
            updatedFavorites.add(currentFact);
            Toast.makeText(this, "факт додано до улюблених", Toast.LENGTH_SHORT).show();
        }

        favoriteFacts = updatedFavorites;
        sharedPreferences.edit().putStringSet("favoriteFacts", favoriteFacts).apply();

        updateFavoriteButtonState();
    }

    private void updateFavoriteButtonState() {
        if (favoriteFacts.contains(currentFact)) {
            favoriteButton.setImageResource(R.drawable.ic_favorite_filled);
        } else {
            favoriteButton.setImageResource(R.drawable.ic_favorite_border);
        }
    }

    private void showFavoritesList() {
        if (favoriteFacts.isEmpty()) {
            Toast.makeText(this, "у вас ще немає улюблених фактів", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] factsArray = favoriteFacts.toArray(new String[0]);

        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        builder.setTitle("улюблені факти")
                .setItems(factsArray, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        currentFact = factsArray[which];
                        factTextView.setText(currentFact);

                        imageProgressBar.setVisibility(View.VISIBLE);
                        executor.execute(() -> {
                            String imageUrl = fetchCatImageUrl();
                            Bitmap catImage = null;
                            boolean isImageFromApi = false;

                            if (imageUrl != null) {
                                catImage = downloadCatImage(imageUrl);
                                if (catImage != null) {
                                    isImageFromApi = true;
                                }
                            }

                            final Bitmap finalCatImage = catImage;
                            final boolean finalIsImageFromApi = isImageFromApi;

                            handler.post(() -> {
                                imageProgressBar.setVisibility(View.GONE);

                                if (finalIsImageFromApi && finalCatImage != null) {
                                    catImageView.setImageBitmap(finalCatImage);
                                } else {
                                    catImageView.setImageResource(getRandomCatImage());
                                }

                                updateFavoriteButtonState();
                            });
                        });
                    }
                })
                .setPositiveButton("закрити", null);

        if (!favoriteFacts.isEmpty()) {
            builder.setNeutralButton("очистити все", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    clearAllFavorites();
                }
            });
        }

        builder.show();
    }

    private void clearAllFavorites() {
        androidx.appcompat.app.AlertDialog.Builder confirmBuilder =
                new androidx.appcompat.app.AlertDialog.Builder(this);

        confirmBuilder.setTitle("видалити всі улюблені?")
                .setMessage("ви впевнені, що хочете видалити всі улюблені факти?")
                .setPositiveButton("так", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        favoriteFacts.clear();
                        sharedPreferences.edit().putStringSet("favoriteFacts", favoriteFacts).apply();
                        Toast.makeText(MainActivity.this, "всі улюблені факти видалено", Toast.LENGTH_SHORT).show();
                        updateFavoriteButtonState();
                    }
                })
                .setNegativeButton("скасувати", null)
                .show();
    }

    private String fetchCatFactFromAPI() {
        HttpURLConnection connection = null;
        String fact = null;

        try {
            String apiUrl = getRandomApiUrl();

            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                fact = parseCatFactFromResponse(response.toString(), apiUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return fact;
    }

    private String getRandomApiUrl() {
        String[] apis = {
                CAT_FACTS_API_URL,
                MEOW_FACTS_API_URL
        };

        return apis[random.nextInt(apis.length)];
    }

    private String parseCatFactFromResponse(String response, String apiUrl) {
        try {
            if (apiUrl.equals(CAT_FACTS_API_URL)) {
                JSONObject jsonObject = new JSONObject(response);
                return jsonObject.getString("fact");
            }
            else if (apiUrl.equals(MEOW_FACTS_API_URL)) {
                JSONObject jsonObject = new JSONObject(response);
                return jsonObject.getJSONArray("data").getString(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String fetchCatImageUrl() {
        HttpURLConnection connection = null;
        String imageUrl = null;

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
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);

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

    private String getLocalRandomCatFact() {
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

        int randomIndex = random.nextInt(catFacts.length);
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

        int randomIndex = random.nextInt(catImages.length);
        return catImages[randomIndex];
    }
}