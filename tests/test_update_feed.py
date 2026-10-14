import unittest,sys,pathlib,copy,tempfile,hashlib
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import publish_update

class FeedTest(unittest.TestCase):
    def setUp(self):
        self.feed={'schema':1,'stable':{'versionCode':10,'versionName':'2.0.0'},'experimental':{'versionCode':16,'versionName':'2.3.2-experimental'}}
    def test_new_experimental_preserves_stable(self):
        result=publish_update.merge(self.feed,{'versionCode':17,'versionName':'2.3.3-experimental'})
        self.assertEqual(result['stable'],self.feed['stable']);self.assertEqual(result['experimental']['versionCode'],17)
    def test_new_stable_preserves_experimental(self):
        result=publish_update.merge(self.feed,{'versionCode':18,'versionName':'2.4.0'})
        self.assertEqual(result['experimental'],self.feed['experimental']);self.assertEqual(result['stable']['versionCode'],18)
    def test_late_older_release_does_not_downgrade(self):
        self.assertEqual(publish_update.merge(self.feed,{'versionCode':15,'versionName':'2.3.1-experimental'}),self.feed)
    def test_same_code_does_not_replace_existing_payload(self):
        self.assertEqual(publish_update.merge(self.feed,{'versionCode':16,'versionName':'2.3.2-experimental','sha256':'other'}),self.feed)
    def test_input_not_mutated(self):
        saved=copy.deepcopy(self.feed);publish_update.merge(self.feed,{'versionCode':17,'versionName':'2.3.3-experimental'});self.assertEqual(saved,self.feed)
    def test_unknown_schema_rejected(self):
        with self.assertRaises(ValueError):publish_update.merge({'schema':9},{'versionCode':1,'versionName':'1.0.0'})
    def test_manifest_and_payload_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory);manifest=root/'manifest.xml';apk=root/'PixelVcrbt-v2.3.3-experimental.apk'
            manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.antigravity.pixelvcrbt" android:versionName="2.3.3-experimental" android:versionCode="17"/>',encoding='utf-8');apk.write_bytes(b'fixture')
            value=publish_update.entry(manifest,apk,'v2.3.3-experimental');self.assertEqual(value['versionCode'],17);self.assertEqual(value['sha256'],hashlib.sha256(b'fixture').hexdigest())
            with self.assertRaises(ValueError):publish_update.entry(manifest,apk,'v2.3.2-experimental')

if __name__=='__main__':unittest.main()
