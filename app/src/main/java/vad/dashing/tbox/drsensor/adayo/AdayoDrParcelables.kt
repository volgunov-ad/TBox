package vad.dashing.tbox.drsensor.adayo

import android.os.Parcel
import android.os.Parcelable

/** Parcel layout matches Adayo `GyroInfo` from adayo.car.jar. */
class AdayoGyroInfo() : Parcelable {
    var axis: Int = 0
    var pitch: Float = 0f
    var yaw: Float = 0f
    var roll: Float = 0f
    var temperature: Float = 0f
    var interval: Long = 0L
    var tickTime: Long = 0L
    var isTiggerElectronic: Boolean = false
    var gear: Byte = 0

    constructor(parcel: Parcel) : this() {
        axis = parcel.readInt()
        pitch = parcel.readFloat()
        yaw = parcel.readFloat()
        roll = parcel.readFloat()
        temperature = parcel.readFloat()
        interval = parcel.readLong()
        tickTime = parcel.readLong()
        isTiggerElectronic = parcel.readInt() == 1
        gear = parcel.readByte()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(axis)
        dest.writeFloat(pitch)
        dest.writeFloat(yaw)
        dest.writeFloat(roll)
        dest.writeFloat(temperature)
        dest.writeLong(interval)
        dest.writeLong(tickTime)
        dest.writeInt(if (isTiggerElectronic) 1 else 0)
        dest.writeByte(gear)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AdayoGyroInfo> {
        override fun createFromParcel(parcel: Parcel): AdayoGyroInfo = AdayoGyroInfo(parcel)
        override fun newArray(size: Int): Array<AdayoGyroInfo?> = arrayOfNulls(size)
    }
}

/** Parcel layout matches Adayo `AcceleratorInfo`. */
class AdayoAcceleratorInfo() : Parcelable {
    var axis: Int = 0
    var pitch: Float = 0f
    var yaw: Float = 0f
    var roll: Float = 0f
    var interval: Long = 0L
    var tickTime: Long = 0L

    constructor(parcel: Parcel) : this() {
        axis = parcel.readInt()
        pitch = parcel.readFloat()
        yaw = parcel.readFloat()
        roll = parcel.readFloat()
        interval = parcel.readLong()
        tickTime = parcel.readLong()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(axis)
        dest.writeFloat(pitch)
        dest.writeFloat(yaw)
        dest.writeFloat(roll)
        dest.writeLong(interval)
        dest.writeLong(tickTime)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AdayoAcceleratorInfo> {
        override fun createFromParcel(parcel: Parcel): AdayoAcceleratorInfo =
            AdayoAcceleratorInfo(parcel)

        override fun newArray(size: Int): Array<AdayoAcceleratorInfo?> = arrayOfNulls(size)
    }
}

/** Parcel layout matches Adayo `PulseInfo`. */
class AdayoPulseInfo() : Parcelable {
    var value: Float = 0f
    var interval: Long = 0L
    var tickTime: Long = 0L
    var gear: Int = 0

    constructor(parcel: Parcel) : this() {
        value = parcel.readFloat()
        interval = parcel.readLong()
        tickTime = parcel.readLong()
        gear = parcel.readInt()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeFloat(value)
        dest.writeLong(interval)
        dest.writeLong(tickTime)
        dest.writeInt(gear)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AdayoPulseInfo> {
        override fun createFromParcel(parcel: Parcel): AdayoPulseInfo = AdayoPulseInfo(parcel)
        override fun newArray(size: Int): Array<AdayoPulseInfo?> = arrayOfNulls(size)
    }
}

/** Parcel layout matches Adayo `MountAngleInfo`. */
class AdayoMountAngleInfo() : Parcelable {
    var isExistMountAngle: Boolean = false
    var pitch: Float = 0f
    var yaw: Float = 0f
    var roll: Float = 0f

    constructor(parcel: Parcel) : this() {
        isExistMountAngle = parcel.readByte() != 0.toByte()
        pitch = parcel.readFloat()
        yaw = parcel.readFloat()
        roll = parcel.readFloat()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(if (isExistMountAngle) 1 else 0)
        dest.writeFloat(pitch)
        dest.writeFloat(yaw)
        dest.writeFloat(roll)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AdayoMountAngleInfo> {
        override fun createFromParcel(parcel: Parcel): AdayoMountAngleInfo =
            AdayoMountAngleInfo(parcel)

        override fun newArray(size: Int): Array<AdayoMountAngleInfo?> = arrayOfNulls(size)
    }
}
