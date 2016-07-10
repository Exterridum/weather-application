package com.example.android.sunshine.app;

/**
 * Created by Hephaestos on 5/10/2016.
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;
    private SharedPreferences prefs;

    public ForecastFragment() {
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Add this line in order for this fragment to handle menu events.
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        FetchWeatherTask task = new FetchWeatherTask();
        //retrive preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // get value of preference, first parameter is key of preference second is default parameter
        // default parameter is here for case when preference dont exist
        String location = sharedPreferences.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
        task.execute(location);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        //In adapter we compose our data with xml files together
        mForecastAdapter = new ArrayAdapter<String>(
                //Current context - this fragmet
                getActivity(),
                //ID of list item layout
                R.layout.list_item_forecast,
                //ID of textview to populate
                R.id.list_item_forecast_textview);

        //Fill listview with our prepair adapter. here we must use rootView because its a fragment
        final ListView forecastListView = (ListView) rootView.findViewById(R.id.listView_forecast);
        forecastListView.setAdapter(mForecastAdapter);
        forecastListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String dayForecast = mForecastAdapter.getItem(position);
                //create new intent and put data into it as message with name EXTRA_TEXT
                Intent intent = new Intent(getActivity() , DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, dayForecast);
                startActivity(intent);
            }
        });


        return rootView;
    }

    /**
     * Async task have own lifecycle onPreExecute() -> doInBackground() -> onProgress() -> onPostExecute(),
     * we dont have worry about inputs to this methods we just need to create this class
     * FetchWeatherTask task = new FetchWeatherTask(); and then we just run execute method.
     * So we dont need to call for example task.doInBackground(Params), ...
     */
    //First is input params, second is progress if we want to check status, Last is result from this operation
    //result is important because only with result we can get data from background thread to UI thread
    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        @Override
        protected String[] doInBackground(String... params) {

            //if we dont have parameters on input we cant change our URL
            if (params.length == 0) {
                return null;
            }
            /**
             * HttpURLConnection operations START
             */
            /**
             * Work order
             * 1. URL -> 2. HttpURLConnection -> 3. InputStream -> 4. BufferedReader -> 5. StringBuffer -> 6. String
             * 1-2. Make Connection from URL / 3. then read InputStream(data from conection) / 4. Load raw data into BufferedReader /
             * 5. Now we read data from BufferedReader line by line and append it into StringBuffer /
             * 6. When we read all data into StringBuffer then we add it into String
             * !! Be carefull this operations cannot be runned in Main thread because main_activity_menu thread is used to UI input and output operations like click, scroll etc
             * This operations must be started on bacground thread that mean Async Task
             */


            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            // HttpURLConnection is better than HttpClient because is better optimised
            // http://android-developers.blogspot.sk/2011/09/androids-http-clients.html
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            //Changable Parameters used to building url
            String API_KEY = "beff9b515621c30c77caa3b08b40b59f";
            String format = "json";
            String units = "metric";
            int days = 7;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                // Used parameters is - Town by postalcode q=98701,sk / APPID is our api key obtained from site when registered
                // We want Json format mode=json / And we need metric units units=metric
                // This parameter limited results to 7 results cnt=7
                // 1. Build url http://api.openweathermap.org/data/2.5/forecast/daily?q=98701,sk&APPID=beff9b515621c30c77caa3b08b40b59f&mode=json&units=metric&cnt=7

                //Static parameters used to building url
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String APPID_PARAM = "APPID";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                //Building our url
                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        //first is key "q" second is value "98701" between this is inserted automaticaly equal sign "="
                        //after each append is added sign "&" wich is our divider between parameters
                        //if we want to add for example sign "/" we use appendPath...
                        .appendQueryParameter(QUERY_PARAM, params[0].toString())
                        .appendQueryParameter(APPID_PARAM, API_KEY)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(days))
                        .build();

                URL url = new URL(builtUri.toString());

                // 2. Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // 3. Read the input stream into String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                //If in inputstream was not created then URL or webpage can be broken
                if (inputStream == null) {
                    //Nothing to do
                    return null;
                }

                // 4.
                reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                // 5.
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                // 6.
                forecastJsonStr = buffer.toString();


            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            }  finally {
                //At end of reading data we must close connection and reader
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            /**
             * HttpURLConnection operations END
             */
            try {
                //this is return vaule for bacground thread
                //if this drop method return null
               return getWeatherDataFromJson(forecastJsonStr, days);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;

        }

        @Override
        // This method is called when doInBackground method is finished, so here we can be 100% sure to have our data
        // String[] result is return value from doInBackground method
        protected void onPostExecute(String[] result) {
            if (result != null) {
                mForecastAdapter.clear();
                for (String s: result) {
                    mForecastAdapter.add(s);
                    // New data is back from the server.  Hooray!
                }

            }
        }

        /* The date/time conversion code is going to be moved outside the asynctask later,
                * so for convenience we're breaking it out into its own method now.
                */
        private String getReadableDateString(long time) {
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low, String unitType) {
            // For presentation, assume the user doesn't care about tenths of a degree.

            if (unitType.equals(getString(R.string.pref_imperial_units_value))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_metric_units_value))) {
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }

            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         * <p/>
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "description";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPreferences.getString(
                    getString(R.string.pref_units_key),
                    getString(R.string.pref_metric_units_value));

            for (int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low, unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStrs;

        }
    }


}
