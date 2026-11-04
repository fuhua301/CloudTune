package com.cloudtune.player;

/** Stable stored identifiers: 8 belonged to the removed speaker preset. */
final class EffectPresets {
    static final int[] IDS={0,1,2,3,4,5,6,7,9,10};
    static final String[] NAMES={"3D丽音","黑钻蝰蛇","LIVE","澎湃低音","纯净人声","宽广环绕","电子管","黑胶唱片","HIFI现场","3D旋转"};
    static final String[] NOTES={"立体环绕声，仿佛歌神降临耳畔。","全频音质修复引擎。","精准还原距离舞台5-10米黄金听位。","自然醇正澎湃重低音驾到，低频动态增强技术，智能防破音。","音色明亮通透，音频相位主动调节。","3维空间声场，动态声音环绕。","6N16B型电子管耳机放大器音色模拟。","唱片机声场效果，带你找回经典的感觉。","演唱会声场效果，HiFi级音质体验，现场人头采样技术，仿佛歌手就在你面前。","360度动感旋转。"};
    static int clamp(int id){for(int n:IDS)if(id==n)return id;return 0;}
    static int index(int id){for(int i=0;i<IDS.length;i++)if(IDS[i]==id)return i;return 0;}
    static String name(int id){return NAMES[index(id)];}
    static String note(int id){return NOTES[index(id)];}
}
