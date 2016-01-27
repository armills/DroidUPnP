/**
 * Copyright (C) 2013 Aurélien Chabot <aurelien@chabot.fr>
 *
 * This file is part of DroidUPNP.
 *
 * DroidUPNP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DroidUPNP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DroidUPNP.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.droidupnp;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v4.widget.DrawerLayout;

import org.droidupnp.controller.upnp.IUpnpServiceController;
import org.droidupnp.model.upnp.IFactory;
import org.droidupnp.view.ContentDirectoryFragment;
import org.droidupnp.view.SettingsActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;

import android.support.v4.view.MenuItemCompat;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.support.v7.app.MediaRouteActionProvider;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.ApplicationConnectionResult;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

public class Main extends ActionBarActivity
{
	private static final String TAG = "Main";

	// Controller
	public static IUpnpServiceController upnpServiceController = null;
	public static IFactory factory = null;

	private static Menu actionBarMenu = null;

	private DrawerFragment mDrawerFragment;
	private CharSequence mTitle;

	private static ContentDirectoryFragment mContentDirectoryFragment;

	private MediaRouter mediaRouter;
	private MediaRouteSelector mediaRouteSelector;
	private CastDevice selectedDevice;
	private GoogleApiClient apiClient;
	private boolean applicationStarted;
	private String sessionId;
	private RemoteMediaPlayer remoteMediaPlayer;

	public static void setContentDirectoryFragment(ContentDirectoryFragment f) {
		mContentDirectoryFragment = f;
	}

	public static ContentDirectoryFragment getContentDirectoryFragment() {
		return mContentDirectoryFragment;
	}

	public static void setSearchVisibility(boolean visibility)
	{
		if(actionBarMenu == null)
			return;

		MenuItem item = actionBarMenu.findItem(R.id.action_search);

		if(item != null)
			item.setVisible(visibility);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Log.d(TAG, "onCreated : " + savedInstanceState + factory + upnpServiceController);

		// Use cling factory
		if (factory == null)
			factory = new org.droidupnp.controller.cling.Factory();

		// Upnp service
		if (upnpServiceController == null)
			upnpServiceController = factory.createUpnpServiceController(this);

		if(getFragmentManager().findFragmentById(R.id.navigation_drawer) instanceof DrawerFragment)
		{
			mDrawerFragment = (DrawerFragment)
					getFragmentManager().findFragmentById(R.id.navigation_drawer);
			mTitle = getTitle();

			// Set up the drawer.
			mDrawerFragment.setUp(
				R.id.navigation_drawer,
				(DrawerLayout) findViewById(R.id.drawer_layout));
		}

		mediaRouter = MediaRouter.getInstance(getApplicationContext());
		mediaRouteSelector = new
			MediaRouteSelector.Builder()
			.addControlCategory(CastMediaControlIntent.categoryForCast
			(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
			.build();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
			MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
	}

	@Override
	protected void onStop()
	{
		mediaRouter.removeCallback(mediaRouterCallback);
		super.onStop();
	}

	@Override
	public void onResume()
	{
		Log.v(TAG, "Resume activity");
		upnpServiceController.resume(this);
		super.onResume();
	}

	@Override
	public void onPause()
	{
		Log.v(TAG, "Pause activity");
		upnpServiceController.pause();
		upnpServiceController.getServiceListener().getServiceConnexion().onServiceDisconnected(null);
		super.onPause();
	}

	public void refresh()
	{
		upnpServiceController.getServiceListener().refresh();
		ContentDirectoryFragment cd = getContentDirectoryFragment();
		if(cd!=null)
			cd.refresh();
	}

	public void restoreActionBar() {
		ActionBar actionBar = getSupportActionBar();
		if(actionBar!=null) {
			actionBar.setDisplayShowTitleEnabled(true);
			actionBar.setTitle(mTitle);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		actionBarMenu = menu;
		restoreActionBar();

		MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
		MediaRouteActionProvider mediaRouteActionProvider =
			(MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
		mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.

		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.menu_refresh:
				refresh();
				break;
			case R.id.menu_settings:
				startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
				break;
			case R.id.menu_quit:
				finish();
				break;
			default:
				return super.onOptionsItemSelected(item);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed()
	{
		ContentDirectoryFragment cd = getContentDirectoryFragment();
		if (cd!=null && !cd.goBack()) {
			return;
		}
		super.onBackPressed();
	}

	private static InetAddress getLocalIpAdressFromIntf(String intfName)
	{
		try
		{
			NetworkInterface intf = NetworkInterface.getByName(intfName);
			if(intf.isUp())
			{
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
				{
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
						return inetAddress;
				}
			}
		} catch (Exception e) {
			Log.w(TAG, "Unable to get ip adress for interface " + intfName);
		}
		return null;
	}

	public static InetAddress getLocalIpAddress(Context ctx) throws UnknownHostException
	{
		WifiManager wifiManager = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		int ipAddress = wifiInfo.getIpAddress();
		if(ipAddress!=0)
			return InetAddress.getByName(String.format("%d.%d.%d.%d",
				(ipAddress & 0xff), (ipAddress >> 8 & 0xff),
				(ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff)));

		Log.d(TAG, "No ip adress available throught wifi manager, try to get it manually");

		InetAddress inetAddress;

		inetAddress = getLocalIpAdressFromIntf("wlan0");
		if(inetAddress!=null)
		{
			Log.d(TAG, "Got an ip for interfarce wlan0");
			return inetAddress;
		}

		inetAddress = getLocalIpAdressFromIntf("usb0");
		if(inetAddress!=null)
		{
			Log.d(TAG, "Got an ip for interfarce usb0");
			return inetAddress;
		}

		return InetAddress.getByName("0.0.0.0");
	}

	private final MediaRouter.Callback mediaRouterCallback = new
	MediaRouter.Callback()
	{
		@Override
		public void onRouteSelected(MediaRouter router, RouteInfo info)
		{
			selectedDevice = CastDevice.getFromBundle(info.getExtras());

			Cast.CastOptions.Builder apiOptionsBuilder =
				Cast.CastOptions.builder(selectedDevice, castClientListener);
			apiClient = new GoogleApiClient.Builder(Main.this)
				.addApi(Cast.API, apiOptionsBuilder.build())
				.addConnectionCallbacks(connectionCallbacks)
				.addOnConnectionFailedListener(connectionFailedListener)
				.build();
			apiClient.connect();
		}

		@Override
		public void onRouteUnselected(MediaRouter router, RouteInfo info)
		{
			teardown();
			selectedDevice = null;
		}
	};

	private final Cast.Listener castClientListener = new Cast.Listener()
	{
		@Override
		public void onApplicationDisconnected(int statusCode)
		{
			teardown();
		}

		@Override
		public void onVolumeChanged()
		{
		}
	};

	private final GoogleApiClient.ConnectionCallbacks connectionCallbacks =
	new GoogleApiClient.ConnectionCallbacks()
	{
		@Override
		public void onConnected(Bundle bundle)
		{
			try
			{
				Cast.CastApi.launchApplication(apiClient,
				CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID, false)
				.setResultCallback(connectionResultCallback);
			}
			catch (Exception e)
			{
				Log.e(TAG, "Failed to launch application", e);
			}
		}

		@Override
		public void onConnectionSuspended(int cause)
		{
		}
	};

	private final GoogleApiClient.OnConnectionFailedListener connectionFailedListener =
	new GoogleApiClient.OnConnectionFailedListener()
	{
		@Override
		public void onConnectionFailed(ConnectionResult connectionResult)
		{
			teardown();
		}
	};

	private final ResultCallback connectionResultCallback = new ResultCallback<Cast.ApplicationConnectionResult>()
	{
		@Override
		public void onResult(Cast.ApplicationConnectionResult result)
		{
			Status status = result.getStatus();
			if (status.isSuccess())
			{
				sessionId = result.getSessionId();
				applicationStarted = true;

				remoteMediaPlayer = new RemoteMediaPlayer();
				remoteMediaPlayer.setOnStatusUpdatedListener(onStatusUpdatedListener);
				remoteMediaPlayer.setOnMetadataUpdatedListener(onMetadataUpdatedListener);

				try {
					Cast.CastApi.setMessageReceivedCallbacks(apiClient,
						remoteMediaPlayer.getNamespace(), remoteMediaPlayer);
				} catch (Exception e) {
					Log.e(TAG, "Exception while creating media channel", e);
				}
				remoteMediaPlayer.requestStatus(apiClient)
					.setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
						@Override
						public void onResult(RemoteMediaPlayer.MediaChannelResult result)
						{
							if (!result.getStatus().isSuccess()) {
								Log.e(TAG, "Failed to request status.");
							}
						}
					});

				// Load test media
				MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW);
				mediaMetadata.putString(MediaMetadata.KEY_TITLE, "My video");
				MediaInfo mediaInfo = new MediaInfo.Builder(
					"http://192.168.1.2/test.mkv")
					.setContentType("video/mkv")
					.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
					.setMetadata(mediaMetadata)
					.build();
				try {
					remoteMediaPlayer.load(apiClient, mediaInfo, true)
						.setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
							@Override
							public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
								if (result.getStatus().isSuccess()) {
									Log.d(TAG, "Media loaded successfully");
								}
							}
						});
				} catch (IllegalStateException e) {
					Log.e(TAG, "Problem occurred with media during loading", e);
				} catch (Exception e) {
					Log.e(TAG, "Problem opening media during loading", e);
				}
			} else {
				teardown();
			}
		}
	};

	private final RemoteMediaPlayer.OnStatusUpdatedListener onStatusUpdatedListener = new RemoteMediaPlayer.OnStatusUpdatedListener()
	{
		@Override
		public  void onStatusUpdated()
		{
			// TODO: Update UI
		}
	};

	private final RemoteMediaPlayer.OnMetadataUpdatedListener onMetadataUpdatedListener = new RemoteMediaPlayer.OnMetadataUpdatedListener()
	{
		@Override
		public void onMetadataUpdated()
		{
		}
	};

	private void teardown() {
		Log.d(TAG, "teardown");
		if (apiClient != null) {
			if (applicationStarted) {
				if (apiClient.isConnected() || apiClient.isConnecting()) {
					try {
						Cast.CastApi.stopApplication(apiClient, sessionId);
						//Channel
					} catch (Exception e) {
						Log.e(TAG, "Exception while removing channel", e);
					}
					apiClient.disconnect();
				}
				applicationStarted = false;
			}
			apiClient = null;
		}
		selectedDevice = null;
		sessionId = null;
	}
}
