import java.io.File;
import brut.androlib.smali.SmaliDecoder;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;

public final class DecodeHelper {
    public static void main(String[] args) {
        SmaliDecoder decoder = new SmaliDecoder(new File(args[0]), false);
        for (Object dex : decoder.mDexContainer.getEntries().values()) {
            decoder.decodeFile((DexBackedDexFile) dex, new File(args[1]));
        }
    }
}
