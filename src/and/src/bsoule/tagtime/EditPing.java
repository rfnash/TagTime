package bsoule.tagtime;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

// ChangeLog:

/* 2013.10.26 Uluc: If the activity is invoked with RowId = null, it will let the user 
 * select a number of tags and send them back to the invking activity in the response. */

public class EditPing extends Activity {

	private final static String TAG = "EditPing";
	private static final boolean LOCAL_LOGV = true && !Timepie.DISABLE_LOGV;

	public static final String KEY_EDIT = "editor";
	public static final String KEY_PTIME = "pingtime";

	private PingsDbAdapter mPingsDB;
	// private EditText mNotesEdit;
	private EditText mTagsEdit = null;
	private TextView mPingTitle;
	private Long mRowId;
	private Long mPingUTC;
	private LinearLayout tagParent;
	private Cursor mTagsCursor;
	private Cursor mTaggings;
	private int FIXTAGS = R.layout.tagtime_editping;
	private ViewTreeObserver vto;

	private boolean landscape;
	private List<String> mCurrentTags;
	private String mCurrentTagString = "";

	public static int LAUNCH_VIEW = 0;
	public static int LAUNCH_NOTE = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tagtime_editping);
		// mNotesEdit = (EditText) findViewById(R.id.notes);

		View v = findViewById(R.id.tags_editText);
		if (v == null) {
			landscape = false;
			tagParent = (LinearLayout) findViewById(R.id.lintags);
			vto = tagParent.getViewTreeObserver();
		} else {
			landscape = true;
			mTagsEdit = (EditText) v;
		}

		// cancel the notification
		// TODO: only cancel note if it is for same ping as we are editing
		NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		nm.cancel(R.layout.tagtime_editping);

		// set up editor:
		mPingTitle = (TextView) findViewById(R.id.pingtime);
		mRowId = getIntent().getLongExtra(PingsDbAdapter.KEY_ROWID, -1);

		mPingsDB = new PingsDbAdapter(this);
		mPingsDB.open();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String ordering = prefs.getString("sortOrderPref", "FREQ");
		if (LOCAL_LOGV)
			Log.w(TAG, "Getting Tags with order: " + ordering);
		mTagsCursor = mPingsDB.fetchAllTags(ordering);
		startManagingCursor(mTagsCursor);
		if (mRowId >= 0) {
			mTaggings = mPingsDB.fetchTaggings(mRowId, PingsDbAdapter.KEY_PID);
			startManagingCursor(mTaggings);
		} else {
			mTaggings = null;
			String savedtags = null;
			if (savedInstanceState != null) {
				savedtags = savedInstanceState.getString("editping_tagsave");
			} else {
				// Look for tag information inthe incoming intent
				Bundle extras = getIntent().getExtras();
				if (extras != null)
					savedtags = extras.getString("tags");
			}
			if (savedtags != null) {
				mCurrentTags = new ArrayList<String>(Arrays.asList(savedtags.split("\\s+")));
				mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			}
		}

		// Set confirm button behaviour
		Button confirm = (Button) findViewById(R.id.confirm);
		confirm.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent resultIntent = new Intent();
				if (landscape) {
					String[] newtagstrings = mTagsEdit.getText().toString().split("\\s+");
					mCurrentTags = new ArrayList<String>(Arrays.asList(newtagstrings));
					mCurrentTagString = TextUtils.join(" ", mCurrentTags);
				}
				resultIntent.putExtra("tags", mCurrentTagString);
				setResult(RESULT_OK, resultIntent);
				finish();
			}
		});
	}

	private void populateFields() {
		if (mRowId >= 0) {
			Cursor note = mPingsDB.fetchPing(mRowId);
			startManagingCursor(note);
			try {
				// set ping time title
				mPingUTC = note.getLong(note.getColumnIndexOrThrow(PingsDbAdapter.KEY_PING));
				SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
				mPingTitle.setText(SDF.format(new Date(mPingUTC * 1000)));
				// get tags
				mCurrentTags = mPingsDB.fetchTagsForPing(mRowId);
				mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			} catch (Exception e) {
				Log.i(TAG, "caught an exception in populateFields():");
				Log.i(TAG, "    " + e.getLocalizedMessage());
				Log.i(TAG, "    " + e.getMessage());
			}
		} else {
			Log.i(TAG, "ROWID was NULL");
			mPingTitle.setText(getText(R.string.editping_selecttags));
			if (mCurrentTags == null)
				mCurrentTags = new ArrayList<String>();
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			// TODO: Update string to be sent back to calling activity here
		}

		if (!landscape) {
			loadTags();
		} else {
			if (mCurrentTags != null)
				mTagsEdit.setText(TextUtils.join(" ", mCurrentTags));
		}
	}

	private void loadTags() {
		tagParent.removeAllViews();
		mTagsCursor.moveToFirst();

		LinearLayout ll = new LinearLayout(this);
		ll.setId(FIXTAGS);
		ll.setOrientation(1);
		ll.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		while (!mTagsCursor.isAfterLast()) {
			String tag = mTagsCursor.getString(mTagsCursor.getColumnIndex(PingsDbAdapter.KEY_TAG));
			long id = mTagsCursor.getLong(mTagsCursor.getColumnIndex(PingsDbAdapter.KEY_ROWID));
			boolean on = false;
			if (mCurrentTags != null)
				on = mCurrentTags.contains(tag);
			TagToggle tog = new TagToggle(this, tag, id, on);
			tog.setOnClickListener(mTogListener);
			ll.addView(tog);

			mTagsCursor.moveToNext();
		}
		tagParent.addView(ll);
		vto.addOnPreDrawListener(mDrawListener);
	}

	private void fixTags() {
		LinearLayout ll = (LinearLayout) tagParent.getChildAt(0);
		tagParent.removeAllViews();
		int pwidth = tagParent.getWidth() - 15;
		int twidth = 0;

		LinearLayout tagrow = new LinearLayout(this);
		tagrow.setOrientation(0);
		tagrow.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		while (ll.getChildCount() > 0) {
			View tog = ll.getChildAt(0);
			ll.removeViewAt(0);
			if ((twidth + tog.getWidth()) > pwidth) {
				tagParent.addView(tagrow);
				tagrow = new LinearLayout(this);// .removeAllViews();
				tagrow.setOrientation(0);
				tagrow.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
				twidth = 0;
			}
			tagrow.addView(tog);
			twidth += tog.getWidth();
		}
		tagParent.addView(tagrow);
	}

	@Override
	protected void onPause() {
		super.onPause();
		saveState();
		if (LOCAL_LOGV)
			Log.i(TAG, "Was paused...");
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (findViewById(R.id.tags_editText) == null) {
			landscape = false;
			if (LOCAL_LOGV)
				Log.i(TAG, "Resuming in PORTRAIT");
		} else {
			landscape = true;
			if (LOCAL_LOGV)
				Log.i(TAG, "Resuming in LANDSCAPE");
		}
		populateFields();
	}

	private void saveState() {
		if (landscape) {
			String[] newtagstrings = mTagsEdit.getText().toString().split("\\s+");
			mCurrentTags = new ArrayList<String>(Arrays.asList(newtagstrings));
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			if (mRowId >= 0)
				mPingsDB.updateTaggings(mRowId, Arrays.asList(newtagstrings));
			else {
				for (String t : mCurrentTags) {
					if (t.trim().length() == 0)
						continue;

					if (LOCAL_LOGV)
						Log.v(TAG, "Creating tag -" + t + "-");
					mPingsDB.getOrMakeNewTID(t);
				}
			}

		} else {
			if (mRowId >= 0) {
				mPingsDB.updateTaggings(mRowId, mCurrentTags);
			} else {
				// Nothing, onSaveInstanceState will take care of saving the
				// current tags for orientation change
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mCurrentTags != null && mRowId < 0) {
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
			outState.putString("editping_tagsave", mCurrentTagString);
		}
	}

	public void startView() {
		startActivity(new Intent(this, ViewLog.class));
	}

	@Override
	protected void onDestroy() {

		mPingsDB.close();
		super.onDestroy();
	}

	private OnPreDrawListener mDrawListener = new OnPreDrawListener() {
		public boolean onPreDraw() {
			fixTags();
			ViewTreeObserver vto = tagParent.getViewTreeObserver();
			vto.removeOnPreDrawListener(mDrawListener);
			return true;
		}
	};

	private OnClickListener mTogListener = new OnClickListener() {
		public void onClick(View v) {
			TagToggle tog = (TagToggle) v;
			if (tog.isSelected()) {
				Log.i(TAG, "toggle selected");
				try {
					mCurrentTags.add(tog.getText().toString());
				} catch (Exception e) {
					Log.e(TAG, "error inserting newTagPing()");
				}
			} else {
				mCurrentTags.remove(tog.getText().toString());
			}
			mCurrentTagString = TextUtils.join(" ", mCurrentTags);
		}

	};

}
