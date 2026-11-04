package com.cloudtune.player;
import android.content.Context;
import android.content.SharedPreferences;
final class AppDefaults {
    static void initialize(Context c){
        SharedPreferences p=c.getSharedPreferences("sound_effects",0);
        SharedPreferences.Editor edit=p.edit();boolean changed=false;
        if(!p.getBoolean("hifi_default_v21",false)){
            edit.putInt("preset",9).putBoolean("enabled",true).putBoolean("hifi_default_v21",true);changed=true;
        }
        if(!p.getBoolean("loudness_default_v24",false)){
            edit.putBoolean("loudness",true).putBoolean("loudness_default_v24",true);changed=true;
        }
        if(changed)edit.commit();
    }
}
