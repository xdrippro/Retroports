package ru.woesss.j2me.installer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.applist.AppListModel;

/**
 * Installs the games bundled in assets/games/*.jar into the emulator on first launch.
 * Each jar is installed only once, so a game the user deletes later is not brought back.
 */
public final class BundledGames {
	private static final String TAG = "BundledGames";
	private static final String ASSET_DIR = "games";
	private static final String PREFS = "bundled_games";

	private BundledGames() {
	}

	/** Blocking: call from a background thread. */
	public static synchronized void installMissing(Context context, AppListModel model) {
		String[] names;
		try {
			names = context.getAssets().list(ASSET_DIR);
		} catch (IOException e) {
			Log.e(TAG, "Can't list assets/" + ASSET_DIR, e);
			return;
		}
		if (names == null || names.length == 0) return;
		File cache = new File(context.getCacheDir(), "bundled");
		if (!cache.isDirectory() && !cache.mkdirs()) return;
		SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

		for (String name : names) {
			if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
			if (prefs.getBoolean(name, false)) continue;
			File jar = new File(cache, name);
			try {
				copyAsset(context, ASSET_DIR + "/" + name, jar);
				AppInstaller installer = new AppInstaller(jar, Uri.fromFile(jar), model);
				int status = Single.<Integer>create(installer::loadInfo).blockingGet();
				if (status == AppInstaller.STATUS_NEW) {
					Single.<Integer>create(installer::install).blockingGet();
				}
				// NEW installed now, or already present (EQUAL/SAME/NEWER...): don't try again
				prefs.edit().putBoolean(name, true).apply();
			} catch (Throwable t) {
				Log.e(TAG, "Install failed: " + name, t);
			} finally {
				//noinspection ResultOfMethodCallIgnored
				jar.delete();
			}
		}
	}

	private static void copyAsset(Context context, String assetPath, File out) throws IOException {
		try (InputStream in = context.getAssets().open(assetPath);
			 OutputStream os = new FileOutputStream(out)) {
			byte[] buf = new byte[16 * 1024];
			int n;
			while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
		}
	}
}
