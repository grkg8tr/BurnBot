package com.nicknackhacks.dailyburn.activity;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;

import oauth.signpost.OAuth;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.flurry.android.FlurryAgent;
import com.google.ads.AdSenseSpec;
import com.google.ads.GoogleAdView;
import com.nicknackhacks.dailyburn.ActionBarHandler;
import com.nicknackhacks.dailyburn.BurnBot;
import com.nicknackhacks.dailyburn.LogHelper;
import com.nicknackhacks.dailyburn.R;

public class MainActivity extends Activity {

	CommonsHttpOAuthConsumer consumer;
	DefaultOAuthProvider provider;

	boolean isAuthenticated;
	private SharedPreferences pref;
	MealNamesAsyncTask mealNameTask;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		LogHelper.LogD("In Create");
		pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
//		pref = this.getSharedPreferences("dbdroid", 0);
		isAuthenticated = pref.getBoolean("isAuthed", false);
		boolean mealNamesRetrieved = pref.getBoolean("mealNamesRetrieved", false);
		consumer = ((BurnBot) getApplication()).getOAuthConsumer();
		provider = new DefaultOAuthProvider(consumer,
				"http://dailyburn.com/api/oauth/request_token",
				"http://dailyburn.com/api/oauth/access_token",
				"http://dailyburn.com/api/oauth/authorize");

		if(!mealNamesRetrieved && isAuthenticated) {
			mealNameTask = new MealNamesAsyncTask();
			mealNameTask.execute();			
		}
		if (!pref.getAll().containsKey(BurnBot.DOFLURRY)) {
			OnClickListener listener = new OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
					final Editor editor = pref.edit();
					switch (which) {
					case Dialog.BUTTON_POSITIVE:
						editor.putBoolean(BurnBot.DOFLURRY, true);
						BurnBot.DoFlurry = true;
						break;
					case Dialog.BUTTON_NEGATIVE:
						editor.putBoolean(BurnBot.DOFLURRY, false);
						BurnBot.DoFlurry = false;
						break;
					}
					editor.commit();
				}
			};
			
			AlertDialog flurryAlert = new AlertDialog.Builder(this)
					.setTitle(R.string.flurry_dialog_title)
					.setMessage(R.string.flurry_dialog_message)
					.setPositiveButton(R.string.enable, listener)
					.setNegativeButton(R.string.disable, listener)
					.setCancelable(false).create();
			flurryAlert.show();
		}
		GoogleAdView googleAdView = (GoogleAdView) findViewById(R.id.adview);
		AdSenseSpec adSenseSpec = BurnBot.getAdSpec();
		googleAdView.showAds(adSenseSpec);
	}

	/* Creates the menu items */
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		menu.findItem(R.id.user_name_menu).setEnabled(isAuthenticated);
		menu.findItem(R.id.food_menu).setEnabled(isAuthenticated);
		return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.authenticate_menu:
			FlurryAgent.onEvent("Click Main Auth Options Item");
			startAuthentication();
			return true;
		case R.id.user_name_menu:
			FlurryAgent.onEvent("Click Main User Options Item");
			startUserActivity();
			return true;
		case R.id.food_menu:
			FlurryAgent.onEvent("Click Main Food Options Item");
			startFoodsActivity();
			return true;
		case R.id.pref_menu:
			startActivity(new Intent(this, EditPreferences.class));
			return true;
		}
		return false;
	}

	@Override
	protected void onStart() {
		super.onStart();
		if(BurnBot.DoFlurry)
			FlurryAgent.onStartSession(this, getString(R.string.flurry_key));
		FlurryAgent.onPageView();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		if(BurnBot.DoFlurry)
			FlurryAgent.onEndSession(this);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		isAuthenticated = pref.getBoolean("isAuthed", false);
		Uri uri = this.getIntent().getData();
		if (!isAuthenticated && uri != null
				&& uri.toString().startsWith(getString(R.string.callbackUrl))) {
			LogHelper.LogD( uri.toString());
			String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);
			try {
				loadProvider();
				// this will populate token and token_secret in consumer
				LogHelper.LogD( "Retrieving Access Token");
				provider.retrieveAccessToken(verifier);
				Editor editor = pref.edit();
				editor.putString("token", provider.getConsumer().getToken());
				editor.putString("secret", provider.getConsumer()
						.getTokenSecret());
				isAuthenticated = true;
				editor.putBoolean("isAuthed", isAuthenticated);
				editor.commit();
				BurnBot app = (BurnBot) getApplication();
				app.setOAuthConsumer(consumer);
				mealNameTask = new MealNamesAsyncTask();
				mealNameTask.execute();
				deleteProviderFile();
			} catch (Exception e) {
				LogHelper.LogE(e.getMessage(), e);
			}
		}
		findViewById(R.id.main_button_food).setEnabled(isAuthenticated);
		findViewById(R.id.main_button_user).setEnabled(isAuthenticated);
		//findViewById(R.id.main_button_diet).setEnabled(isAuthenticated);
		findViewById(R.id.main_button_metrics).setEnabled(isAuthenticated);
		
		View btn = findViewById(R.id.main_button_auth);
		btn.setEnabled(!isAuthenticated);
		btn.setVisibility(isAuthenticated ? View.INVISIBLE : View.VISIBLE);
		
		findViewById(R.id.ab_search).setOnClickListener(new ActionBarHandler(this));
		findViewById(R.id.ab_barcode).setOnClickListener(new ActionBarHandler(this));
	}

	protected void loadProvider() {
		LogHelper.LogD( "Loading provider");
		try {
			FileInputStream fin = this.openFileInput("provider.dat");
			ObjectInputStream ois = new ObjectInputStream(fin);
			this.provider = (DefaultOAuthProvider) ois.readObject();
			ois.close();
			consumer = (CommonsHttpOAuthConsumer) this.provider.getConsumer();
		} catch (FileNotFoundException e) {
			LogHelper.LogD( e.getMessage(), e);
		} catch (StreamCorruptedException e) {
			LogHelper.LogD( e.getMessage(), e);
		} catch (IOException e) {
			LogHelper.LogD( e.getMessage(), e);
		} catch (ClassNotFoundException e) {
			LogHelper.LogD( e.getMessage(), e);
		}
		LogHelper.LogD( "Loaded Provider");
	}

	protected void persistProvider() {
		LogHelper.LogD( "Provider Persisting");
		try {
			FileOutputStream fout = this.openFileOutput("provider.dat",
					Context.MODE_PRIVATE);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(this.provider);
			oos.close();
		} catch (FileNotFoundException e) {
			LogHelper.LogE(e.getMessage(), e);
		} catch (IOException e) {
			LogHelper.LogE(e.getMessage(), e);
		}
		LogHelper.LogD( "Provider Persisted");
	}

	protected void deleteProviderFile() {
		this.deleteFile("provider.dat");
	}

	private void startAuthentication() {
		String authUrl;
		if(isAuthenticated)
			FlurryAgent.onEvent("Re-authenticate");
		try {
			authUrl = provider
					.retrieveRequestToken(getString(R.string.callbackUrl));
			persistProvider();
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
		} catch (OAuthMessageSignerException e) {
			LogHelper.LogE(e.getMessage(), e);
		} catch (OAuthNotAuthorizedException e) {
			LogHelper.LogE(e.getMessage(), e);
		} catch (OAuthExpectationFailedException e) {
			LogHelper.LogE(e.getMessage(), e);
		} catch (OAuthCommunicationException e) {
			LogHelper.LogE(e.getMessage(), e);
		}
	}

	private void startUserActivity() {
		Intent intent = new Intent(this, UserActivity.class);
		startActivity(intent);
	}

	private void startFoodsActivity() {
		Intent intent = new Intent(this, FoodSearchActivity.class);
		startActivity(intent);
	}
	
	private void startDietActivity() {
		Intent intent = new Intent(this, DietGoalsActivity.class);
		startActivity(intent);
	}
	
	private void startMetricsActivity() {
		Intent intent = new Intent(this, BodyMetricsListActivity.class);
		startActivity(intent);
	}

	public void onClickFoodButton(View v) {
		FlurryAgent.onEvent("Click Main Food Button");
		startFoodsActivity();
	}

	public void onClickUserButton(View v) {
		FlurryAgent.onEvent("Click Main User Button");
		startUserActivity();
	}

	public void onClickAuthButton(View v) {
		FlurryAgent.onEvent("Click Main Auth Button");
		startAuthentication();
	}
	
	public void onClickDietButton(View v) {
		FlurryAgent.onEvent("Click Main Diet Button");
		startDietActivity();
	}
	
	public void onClickMetricsButton(View v) {
		FlurryAgent.onEvent("Click Main Body Metrics Button");
		startMetricsActivity();
	}
	
	private class MealNamesAsyncTask extends AsyncTask<Void, Void, Void> {

		BurnBot app = null;
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			app = (BurnBot) getApplication();
		}

		@Override
		protected Void doInBackground(Void...voids) {
//			app.loadMealNames(true);
			if(app.retrieveAndStoreMealNames()) {
				Editor editor = pref.edit();
				editor.putBoolean("mealNamesRetrieved", true);
				editor.commit();
			}
			return null;
		}
	}

}