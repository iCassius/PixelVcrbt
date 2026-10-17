package io.github.icassius.pixelvcrbt;

/** Unknown data is not a user-disabled setting. null means the read cannot be interpreted. */
final class SwitchValue {
    static Boolean parse(String value,boolean inverted){
        if(!"0".equals(value)&&!"1".equals(value))return null;
        boolean enabled="1".equals(value);return inverted?!enabled:enabled;
    }
}
