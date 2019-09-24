package org.dhamma.dds;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import org.dhamma.dds.AnyDBAdapter;


import android.app.Activity;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.VideoView;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class DDSActivity extends Activity {
    /** Called when the activity is first created. */
	AnyDBAdapter dba;
	private Timer myTimer;
	boolean enabled = true, loaded = false, playing = false, IS_VIDEO = false;
	String next_event_name, next_event_type, current_filename, current_folder="";
	int duration = 0, next_event_time = 0, last_event_time = 0, last_played_event = 0, current_index = 0, last_index = 0;
	int real_event_time = 0, event_id = 0, temp_id_1 =0 , temp_id_2=0;
	MediaPlayer audio_player;
	VideoView video_view;
	static String MEDIA_PATH = "/sdcard/tflash/dds/";
	SharedPreferences settings;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
		
    }

	protected void onStart() {
		super.onStart();
		System.out.println("I am called - start");
		settings = getPreferences(Activity.MODE_PRIVATE);	
		MEDIA_PATH = settings.getString("sdcard_path", "/sdcard/tflash/dds/");
		
		EditText ed = (EditText)findViewById(R.id.txt_path);
		ed.setText(MEDIA_PATH);
		
    	dba = new AnyDBAdapter(this, MEDIA_PATH); 
    	dba.open();
    	video_view = (VideoView)findViewById(R.id.v1);
    	video_view.setVisibility(View.GONE);
		enabled = true;
		
		SeekBar pb = (SeekBar)findViewById(R.id.progress_bar);
		pb.setVisibility(View.INVISIBLE);
    	
    	ToggleButton tb = (ToggleButton)findViewById(R.id.toggle_btn);
    	tb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				enabled =  isChecked;
				if ( enabled )
					enable_events();
				else
					disable_events();
			}
		});
		tb.setChecked(true);
    	
		myTimer = new Timer();
		myTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				timer_method();
			}
		}, 0, 1000);

		//SeekBar pb = (SeekBar)findViewById(R.id.progress_bar);
		pb.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// TODO Auto-generated method stub
				seekChange(v);
				return false;
			}
		});
	}
	
	protected void onStop() {
		super.onStop();
		dba.close();
		myTimer.cancel();
		//audio_player.release();
	}
	
	
	private void timer_method()
	{
		//This method is called directly by the timer
		//and runs in the same thread as the timer.

		//We call the method that will work with the UI
		//through the runOnUiThread method.
		this.runOnUiThread(update_time);
	}

	private Runnable update_time = new Runnable() {
	    
		public void run() {
			SimpleDateFormat formatter = new SimpleDateFormat("hh:mm:ss a");
			TextView t;
			t=(TextView)findViewById(R.id.lbl_time);
			t.setText(formatter.format(new Date()));
		
			if (enabled) 
			{
				if ( playing )
					update_time_left();
				else
					get_next_event();
			}
		}
	};
	
	protected void onResume()
	{
		super.onResume();
	}
	
	protected void onDestroy() {
		super.onDestroy();
		dba.close();
	}
	
	public void toggle_all(int visible) 
	{
		TextView tv;

		tv = (TextView)findViewById(R.id.lbl_event_time);
		tv.setVisibility(visible);

		tv = (TextView)findViewById(R.id.lbl_event_name);
		tv.setVisibility(visible);

		tv = (TextView)findViewById(R.id.lbl_time_left);
		tv.setText("");
		
		Button b;
		b = (Button)findViewById(R.id.btn_play);
		b.setVisibility(visible);

		b = (Button)findViewById(R.id.btn_stop);
		b.setVisibility(visible);
		
	}

	public void update_info(String str)
	{
		TextView tv;
		toggle_all(View.INVISIBLE);
		
		tv = (TextView)findViewById(R.id.lbl_event_name);
		tv.setGravity(Gravity.CENTER_HORIZONTAL);
		tv.setText(str);
		tv.setVisibility(View.VISIBLE);
	}
	
	public void disable_events() 
	{
		last_event_time = 0;
		next_event_time = 0 ;
		update_info("All events are DISABLED!");
	}

	public void enable_events() 
	{
		TextView tv;
		dba.close();
		dba = new AnyDBAdapter(this, MEDIA_PATH);
		dba.open();

		
		tv = (TextView)findViewById(R.id.lbl_event_name);
		tv.setGravity(Gravity.LEFT);
		tv.setText("Event Name: ");
		
		toggle_all(View.VISIBLE);
	}
	
	public boolean get_next_event() 
	{
		Cursor c;
		Boolean exists = false;
		String temp, temp_fname, temp_name_1 ="",temp_name_2="", temp_et_1="", temp_et_2 = "";
		String temp_folder_1 = "", temp_folder_2 = "";
		int etime, temp_start_time = 0, temp_end_time = 0, temp_end_start_time = 0, mins;
		int temp_lp_1=-1, temp_lp_2=-1;
		SimpleDateFormat formatter = new SimpleDateFormat("HHmm");
		
		Calendar now = Calendar.getInstance();
		//now.add(Calendar.MINUTE, -30);
		//Calendar nowMinus30Minutes = now;
		temp = formatter.format(now.getTime());
		etime = Integer.parseInt(temp);

		/* If the next event was already fetched just wait till the time is equal to next event time*/
		if ( next_event_time > etime )
			return true;
		
		if ( (etime == next_event_time) && (last_played_event != next_event_time) )
		{
			last_played_event = next_event_time;
			handle_player();
		}
		/* Ok some calculation here. There may be a start time or an end time. For end time
		 * we have to calculate backwards to find the start time and then see which should start first
		 * */
		/* First we get the next start time events */
		String qry = "select event_time,name,event_type,last_played,folder,id from schedule where event_time >= "+etime+" and event_type='start' and active=1 order by event_time limit 1";
		try {
			c = dba.MySelect(qry);
		    this.startManagingCursor(c);
		    if ( c.moveToFirst() )
		    {
		    	temp_start_time = c.getInt(0);
		    	temp_name_1 = c.getString(1);
		    	temp_et_1 = c.getString(2);
		    	temp_lp_1 = c.getInt(3);
		    	temp_folder_1 = c.getString(4);
		    	temp_id_1 = c.getInt(5);
		    }
		    c.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		
		/* Next we get the next end time events */
		qry = "select event_time,name,event_type,last_played,folder,id from schedule where event_time > "+etime+" and event_type='end' and active=1 order by event_time limit 1";
		try {
			c = dba.MySelect(qry);
		    this.startManagingCursor(c);
		    if ( c.moveToFirst() )
		    	temp_end_time = c.getInt(0);
		    	temp_name_2 = c.getString(1);
		    	temp_et_2 = c.getString(2);
		    	temp_lp_2 = c.getInt(3);
		    	temp_folder_2 = c.getString(4);
		    	temp_id_2 = c.getInt(5);
		    	
		    	temp_fname = get_next_file_from_folder(temp_folder_2, temp_lp_2);
		    	mins = get_duration_mins(temp_fname);
		    	if ( mins > 0 )
		    	{
		    		Calendar cal_end_time = Calendar.getInstance();
		    		cal_end_time.set(Calendar.HOUR_OF_DAY, temp_end_time/100);
		    		cal_end_time.set(Calendar.MINUTE, temp_end_time%100);
					cal_end_time.add(Calendar.MINUTE, -mins);
					Calendar now_minus_track_length = cal_end_time;
			    	temp_end_start_time = Integer.parseInt(formatter.format(now_minus_track_length.getTime()));
		    	}
		    	else
		    	{
		       		update_info("No files in folder "+temp_folder_2);		    		
		    	}
		    	c.close();
		} catch (Exception e) {
			// TODO: handle exception
		}
		
		if (( temp_end_start_time <= 0 ) && (temp_start_time <= 0 ))
		{
    		update_info("No more events for the day!");
			return exists;
		}
		exists = true;
		
		if ( (temp_end_start_time > etime ) && (temp_end_start_time < temp_start_time) )
		{
			next_event_time = temp_end_start_time;
			next_event_name = temp_name_2;
			current_index = temp_lp_2;
			next_event_type = temp_et_2;
			current_folder = temp_folder_2;
			event_id = temp_id_2;
		}
		else
		{
			next_event_time = temp_start_time;
			next_event_name = temp_name_1;
			current_index = temp_lp_1;
			next_event_type = temp_et_1;
			current_folder = temp_folder_1;
			event_id = temp_id_1;
		}

		if ( last_event_time != next_event_time )
		{
			current_filename = get_next_file_from_folder(current_folder, current_index);
			if ( current_filename == "" )
				update_info("No files in folders "+current_folder);
			update_ui();
		}
		last_event_time = next_event_time;
		
		
/*		qry = "select event_time,name,event_type from schedule where event_time >= "+etime+" and event_type='start' and active=1 order by event_time limit 1";
		try {
		    c = dba.MySelect(qry);
	    	if(c.moveToFirst()) 
	    	{
	    		next_event_time = c.getInt(0);
	    		next_event_name = c.getString(1);
	    		next_event_type = c.getString(2);
	    		if ( last_event_time != next_event_time )
	    		{
	    			update_ui();
	    		}
	    		last_event_time = next_event_time;
	    		exists = true;
	    	}
	    	else
	    		update_info("No more events for the day!");
			c.close();
		} catch (Exception e)  {
			e.printStackTrace();
		}
		*/
		return exists;
	}
	
	public void update_ui() 
	{
		SimpleDateFormat formatter = new SimpleDateFormat("hh:mm a");
		Calendar now = Calendar.getInstance();
		BigDecimal bd = new BigDecimal(next_event_time/100);
		bd = bd.setScale(2, BigDecimal.ROUND_UNNECESSARY);
		now.set(Calendar.HOUR_OF_DAY, bd.intValue());
		now.set(Calendar.MINUTE, next_event_time%100);
		
		TextView tv;
		tv = (TextView)findViewById(R.id.lbl_event_time);
		tv.setText("Event At: "+formatter.format(now.getTime()) + "( "+current_filename.replace(MEDIA_PATH, "")+" )");
		
		tv = (TextView)findViewById(R.id.lbl_event_name);
		tv.setText("Event Name: "+next_event_name);

	}
	
	public void init_audio_player() 
	{
		try {
			audio_player.release();
		} catch (Exception e) {
			// TODO: handle exception
		}
		audio_player = new MediaPlayer();
		//current_filename = get_next_file_from_folder(String.valueOf(next_event_time), last_index) ;//MEDIA_PATH + next_event_track;
		try {
			//audio_player.setAudioStreamType(AudioManager.STREAM_ALARM);
			audio_player.setOnErrorListener(new OnErrorListener() {
				@Override
				public boolean onError(MediaPlayer mp, int what, int extra) {
			        System.out.println( "onError--->   what:" + what + "    extra:" + extra);
			        if (mp != null) {
			            mp.stop();
			            mp.release();
			        }
					return false;
				}
			});
			
			audio_player.setOnCompletionListener(new OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					stop_player();
				}
			}); 
			
			audio_player.setDataSource(current_filename);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			audio_player.prepare();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		try {
			//duration = (int) Math.ceil( audio_player.getDuration() / (60*1000) );
			duration =  audio_player.getDuration();
			//audio_player.start();
			SeekBar pb = (SeekBar)findViewById(R.id.progress_bar);
			pb.setMax(duration);
			pb.setVisibility(View.VISIBLE);
			loaded = true;
			TextView tv = (TextView)findViewById(R.id.lbl_time_left);
			tv.setVisibility(View.VISIBLE);
			//tv.setText("Duration: "+((int) Math.round( (float)audio_player.getDuration() / (60*1000) ))+" mins");
			
		} catch (Exception e) {
			// TODO: handle exception
		}
		
	}
	
	void init_video_player() 
	{
		IS_VIDEO = true;
		//current_filename = MEDIA_PATH + next_event_track;
		video_view.setVideoPath(current_filename);
		video_view.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				stop_player();
			}
		});

		video_view.setOnErrorListener(new OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				// TODO Auto-generated method stub
				System.out.println("I am called");
				stop_player();
				return false;
			}
		});
		video_view.setMediaController(new MediaController(this));
		video_view.requestFocus();			
		video_view.setVisibility(View.VISIBLE);
		
		loaded = true;
	}

	
	public void update_time_left() {
		if ( IS_VIDEO )
			return; 
		if ( audio_player == null)
			return;

		if ( audio_player.isPlaying() )
		{
			//tp = tp + 1;
			int mins, secs, t;
			t = (duration - audio_player.getCurrentPosition())/1000;
			mins = t/60;
			secs = t%60;
			TextView tv = (TextView)findViewById(R.id.lbl_time_left);
			tv.setText( "Time Left: " + String.format("%02d: %02d", mins, secs));
		
			SeekBar pb = (SeekBar)findViewById(R.id.progress_bar);
			pb.setProgress(audio_player.getCurrentPosition()) ;//(audio_player.getCurrentPosition()/duration)*100);
	
		}
	}
	
	
	
	public void handle_player()
	{
		//File f = new File(MEDIA_PATH + next_event_track);
		File f = new File(current_filename);
		if ( ! f.exists() )
		{
			TextView tv = (TextView)findViewById(R.id.lbl_time_left);
			tv.setVisibility(View.VISIBLE);
			tv.setText("File does not exist");
			return;
		}
		Button b = (Button)findViewById(R.id.btn_play);
		if (b.getText().equals("Play"))
		{
			b.setText("Pause");
			if (current_filename.endsWith(".mp4"))
			{
				if ( !loaded )
					init_video_player();
				video_view.start();

			}
			else
			{
				if ( audio_player == null)
					init_audio_player();
				audio_player.start();
			}
			playing = true;
		}
		else
		{
			b.setText("Play");
			if ( IS_VIDEO )
			{
				if (video_view.isPlaying())
					video_view.pause();
			}
			else
			{
				if ( audio_player != null )
					if ( audio_player.isPlaying() )
						audio_player.pause();
			}
		}
		
	}
	
	public void stop_player() 
	{
		update_last_played(event_id);
		Button b = (Button)findViewById(R.id.btn_play);
		b.setText("Play");
		
		SeekBar pb = (SeekBar)findViewById(R.id.progress_bar);
		pb.setVisibility(View.INVISIBLE);

		if ( IS_VIDEO )
		{
			try {
				video_view.stopPlayback();
			} catch (Exception e) {
				// TODO: handle exception
			}
			video_view.setVisibility(View.GONE);
		}
		else
		if ( audio_player != null )
			if (audio_player.isPlaying())
				audio_player.stop();
		try {
			audio_player.release();
		} catch (Exception e) {
			// TODO: handle exception
		}
		audio_player = null;
		
		playing = false;
		loaded = false;
		IS_VIDEO = false;
		TextView tv = (TextView)findViewById(R.id.lbl_time_left);
		tv.setText("");
	}
	
	private void seekChange(View v) {
		if ( IS_VIDEO )
		{
			SeekBar sb = (SeekBar)v;
			video_view.seekTo(sb.getProgress());
		}
		else
		if(audio_player.isPlaying())
		{
			SeekBar sb = (SeekBar)v;
			audio_player.seekTo(sb.getProgress());
		}
	}

	public void play_click( View view ) 
	{
		handle_player();
	}
	
	public void stop_click(View view )
	{
		stop_player();
	}
	
	public String get_next_file_from_folder(String folder, int last_played) 
	{
		String fname = "";
		current_index = last_played + 1;
		File f = new File(MEDIA_PATH+folder);
		if (!f.exists())
			return "";
		
		FileFilter ff = new FileFilter() {
			
			@Override
			public boolean accept(File pathname) {
				if (pathname.getPath().endsWith(".mp3") || pathname.getPath().endsWith(".mp4"))
					return true;
				else
					return false;
			}
		};
		
		File[] files = f.listFiles(ff);
		int count = files.length - 1;
		if ( current_index > count )
			current_index = 0;
		fname = files[current_index].getPath();

		return fname;
	}

	public void update_last_played( int event_id )
	{
		dba.MyCommand("update schedule set last_played="+current_index+" where id="+event_id);
	}
	
	public int get_duration_mins(String filename)
	{
		int duration = -1;
		File f = new File(filename);
		if ( ! f.exists() )
			return duration;
		MediaPlayer mp = new MediaPlayer();
		try {
			mp.setDataSource(filename);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			mp.prepare();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			//duration = mp.getDuration();
			duration = Math.round( (float)mp.getDuration() / (60*1000) );
			mp.release();
			
		} catch (Exception e) {
			// TODO: handle exception
		}
		
		return duration;
	}
	
	public void save_settings(View v)
	{
		EditText ed = (EditText)findViewById(R.id.txt_path);
		Editable path = ed.getText();
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("sdcard_path", path.toString());
		MEDIA_PATH = path.toString();
		editor.commit();
		disable_events();
		enable_events();
	}
	
}