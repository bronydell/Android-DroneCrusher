package com.equestriadev.dontcrashmydrone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    //Views
    private GoogleMap mMap;
    private ActionBar bar;
    private FloatingActionButton locate;
    private CoordinatorLayout coordinatorLayout;
    private TextView status;
    private Menu menu;


    //Varibles
    private float zoomLevel = 13.5f;
    private static final int LOCATION = 1;
    private Location oldLocation;
    private SharedPreferences default_pref;
    private String ip = "139.59.130.94";
    private Weather lastWeather;
    private Toast toast;

    //Layers props
    private boolean air, mil, park;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.snackbarPosition);
        status = (TextView)findViewById(R.id.textView);
        bar = this.getSupportActionBar();
        default_pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        toast = new Toast(getApplicationContext());
        toast.setDuration(Toast.LENGTH_LONG);
        getFilters();
        setupMapIfNeeded();
        //zoomLevel=Float.parseFloat(default_pref.getString("max_distance", "12"))*5;
        mapFragment.getMapAsync(this);

    }

    private void getFilters() {
        air = default_pref.getBoolean("show_airports", true);
        mil = default_pref.getBoolean("show_military", true);
        park = default_pref.getBoolean("show_park", true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        everyThingAboutPermissions();
        locate = (FloatingActionButton) findViewById(R.id.localeButt);
        locate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMap != null)
                    if (mMap.getMyLocation() != null)
                        goToMyLatLng(mMap.getMyLocation());
            }
        });
    }

    private void everyThingAboutPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                final Activity activity = MainActivity.this;

                new AlertDialog.Builder(activity)
                        .setTitle("Access denied")
                        .setMessage("Give a location?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                startInstalledAppDetailsActivity(activity);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        MapStateManager mgr = new MapStateManager(this);
        CameraPosition position = mgr.getSavedCameraPosition();
        if (position != null) {
            CameraUpdate update = CameraUpdateFactory.newCameraPosition(position);
            //Toast.makeText(this, "entering Resume State", Toast.LENGTH_SHORT).show();
            mMap.moveCamera(update);

            mMap.setMapType(mgr.getSavedMapType());
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
            // BE CAREFUL WITH THIS LINE!
            mMap.setOnMapLongClickListener(this);
            mMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location location) {
                    //If we moved more than 800m, then we send out position on server again!
                    if (oldLocation != null) {
                        if (Math.abs(location.getLongitude() - oldLocation.getLongitude()) > 0.008 || Math.abs(location.getLatitude() - oldLocation.getLatitude()) > 0.008) {
                            updatePosition(location);
                        }
                    } else {
                        updatePosition(location);
                    }
                }
            });


        }


    }

    private void updatePosition(Location location) {

        oldLocation = location;
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), zoomLevel));
        Log.d("Debug", getURL(location));
        new ParseData().execute(getURL(location), getWeaterURL(location));
    }

    private String getURL(Location location) {
        return "http://" + ip + ":8080/restrictions/area?long=" + location.getLongitude() + "&lat=" + location.getLatitude() + "&dist=" + Float.parseFloat(default_pref.getString("max_distance", "10"));
    }

    private String getWeaterURL(Location location) {
        return "http://" + ip + ":8080/restrictions/weather?long=" + location.getLongitude() + "&lat=" + location.getLatitude() + "&wr=" + Float.parseFloat(default_pref.getString("max_wind", "10"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        MapStateManager mgr = new MapStateManager(this);
        mgr.saveMapState(mMap);
        //Toast.makeText(this, "Map State has been save?", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupMapIfNeeded();
        everyThingAboutPermissions();
        getFilters();
        //zoomLevel=Float.parseFloat(default_pref.getString("max_distance", "12"))*10;
        if (mMap != null)
            if(mMap.isMyLocationEnabled())
                if (mMap.getMyLocation() != null) {
                new ParseData().execute(getURL(mMap.getMyLocation()), getWeaterURL(mMap.getMyLocation()));

            }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        this.menu = menu;
        inflater.inflate(R.menu.mainmennu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings: openSettings(); break;
            case R.id.weather: toast.cancel(); toast.makeText(getApplicationContext(), lastWeather.getTemp() + "°C. Wind: " + lastWeather.getWind_speed() + "km/h. Direction: " + lastWeather.getWind_dir(), Toast.LENGTH_LONG).show(); break;
        }
        return true;
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void goToMyLatLng(Location latLng) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latLng.getLatitude(), latLng.getLongitude()), zoomLevel));
    }

    private Drawable scaleImage (Drawable image, float scaleFactor) {

        if ((image == null) || !(image instanceof BitmapDrawable)) {
            return image;
        }

        Bitmap b = ((BitmapDrawable)image).getBitmap();

        int sizeX = Math.round(image.getIntrinsicWidth() * scaleFactor);
        int sizeY = Math.round(image.getIntrinsicHeight() * scaleFactor);

        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, sizeX, sizeY, false);

        image = new BitmapDrawable(getResources(), bitmapResized);

        return image;

    }

    private void setupMapIfNeeded() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        if (mMap == null) {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
        }
    }

    // Callback with the request from calling requestPermissions(...)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        // Make sure it's our original READ_CONTACTS request
        if (requestCode == LOCATION) {
            if (grantResults.length == 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                if (mMap.getMyLocation() != null)
                    new ParseData().execute(getURL(mMap.getMyLocation()), getWeaterURL(mMap.getMyLocation()));
            } else {
                //Toast.makeText(this, "DAFUQ?", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public static void startInstalledAppDetailsActivity(final Activity context) {
        if (context == null) {
            return;
        }
        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(i);
    }

    private class ParseData extends AsyncTask<String, Void, Void> {
        OkHttpClient client = new OkHttpClient();
        String resp;
        String weatherResponse;

        Snackbar snackbar;
        int flight_status;

        @Override
        protected void onPreExecute() {
            // Runs on the UI thread before doInBackground
            // Good for toggling visibility of a progress indicator
            snackbar = Snackbar
                    .make(coordinatorLayout, "Loading restricted areas...", Snackbar.LENGTH_INDEFINITE);
            flight_status = 3;
            snackbar.show();
            mMap.clear();
        }

        @Override
        protected Void doInBackground(String... strings) {
            // Some long-running task like downloading an image.
            try {
                resp = run(strings[0]);
                weatherResponse = run(strings[1]);
                Log.d("Debug", strings[1]);

            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            drawRestrict();
            getWeather();
            if(flight_status<=0)
            {
                status.setText(getResources().getText(R.string.danger));
                status.setTextColor(getResources().getColor(R.color.danger));
            }
            else if(flight_status==1)
            {
                status.setText(getResources().getText(R.string.risky));
                status.setTextColor(getResources().getColor(R.color.risk));
            }
            else
            {
                status.setText(getResources().getText(R.string.accept));
                status.setTextColor(getResources().getColor(R.color.agree));
            }
            //return null;
        }

        private void drawRestrict() {
            JSONObject obj = null;
            try {
                if (resp != null) {
                    obj = new JSONObject(resp);

                    JSONArray polygons = obj.getJSONArray("restricted_areas");
                    Log.d("Debug", "Doge");
                    for (int k = 0; k < 3; k++) {

                        if (!air && k == 0)
                            continue;
                        if (!mil && k == 1)
                            continue;
                        if (!park && k == 2)
                            continue;
                        JSONArray type = polygons.getJSONArray(k);
                        for (int i = 0; i < type.length(); i++) {
                            JSONArray polygon = type.getJSONArray(i);
                            PolygonOptions polyOptions = new PolygonOptions();
                            for (int j = 0; j < polygon.length(); j++) {
                                JSONObject edge = polygon.getJSONObject(j);
                                polyOptions.add(new LatLng(edge.getDouble("longitude"), edge.getDouble("latitude")));
                            }
                                polyOptions
                                        .strokeColor(Color.argb(200, 170, 57, 57))
                                        .fillColor(Color.argb(100, 214, 53, 53));
                            mMap.addPolygon(polyOptions);
                        }
                    }
                    if(!obj.getBoolean("flight_status"))
                        flight_status--;
                    if (snackbar != null)
                        if (snackbar.isShown()) {
                            snackbar.dismiss();
                            locate.setTranslationY(0);
                        }
                } else {
                    Error("Timeout restricted areas error...");
                }


            } catch (JSONException e) {
                Error("Parse restricted areas error...");
                e.printStackTrace();
            }

        }

        private void getWeather() {
            JSONObject obj = null;
            try {
                if (weatherResponse != null) {
                    Weather cur = new Weather();
                    obj = new JSONObject(weatherResponse);
                    Log.d("Debug", obj.toString());
                    cur.setStatus(obj.getString("weather"));
                    cur.setCanFly(obj.getBoolean("flight_status"));
                    cur.setTemp(BigDecimal.valueOf(obj.getDouble("temp_c")).floatValue());
                    cur.setWind_dir(obj.getString("wind_dir"));
                    cur.setWind_speed(BigDecimal.valueOf(obj.getDouble("wind_kph")).floatValue());
                    lastWeather = cur;
                    if (!cur.isCanFly())
                        flight_status--;
                    Picasso.with(getApplicationContext())
                            .load(obj.getString("icon_url"))
                            .into(new Target()
                            {
                                @Override
                                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from)
                                {
                                    Drawable d = new BitmapDrawable(getResources(), bitmap);
                                    menu.getItem(1).setIcon(scaleImage(d, 2));
                                }

                                @Override
                                public void onBitmapFailed(Drawable errorDrawable)
                                {
                                }

                                @Override
                                public void onPrepareLoad(Drawable placeHolderDrawable)
                                {
                                }
                            });
                }
                else{
                    Error("Weather timeout error...");
                }
            } catch (JSONException e) {
                Error("Weather parse error...");
                e.printStackTrace();
            }
        }


        private void Error(String error) {
            snackbar.setText(error);
            snackbar.setAction("Try again", new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    snackbar.dismiss();
                    locate.setTranslationY(0);
                    if (mMap != null)
                        if(mMap.isMyLocationEnabled())
                            if (mMap.getMyLocation() != null) {
                            new ParseData().execute(getURL(mMap.getMyLocation()), getWeaterURL(mMap.getMyLocation()));
                        }
                }
            });
        }

        String run(String url) throws IOException {

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            return response.body().string();
        }
    }

    @Override
    public void onMapLongClick(LatLng location) {
        new ParseData().execute(getURL(LattoLoc(location)), getWeaterURL(LattoLoc(location)));
    }

    public Location LattoLoc(LatLng location) {
        Location loc = new Location("");
        loc.setLatitude(location.latitude);
        loc.setLongitude(location.longitude);
        return loc;
    }

}
