import pathlib,struct,unittest,zipfile,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ModernPackageTest(unittest.TestCase):
 def test_entry_scope_and_props(self):
  with zipfile.ZipFile(ROOT/'PixelVcrbt.apk') as z:
   self.assertNotIn('assets/xposed_init',z.namelist())
   self.assertEqual(z.read('META-INF/xposed/java_init.list').decode().splitlines(),['io.github.icassius.pixelvcrbt.PixelVcrbtBridge'])
   self.assertEqual(z.read('META-INF/xposed/scope.list').decode().splitlines(),['com.google.android.dialer'])
   props=dict(x.split('=',1) for x in z.read('META-INF/xposed/module.prop').decode().splitlines())
   self.assertEqual(props,{'minApiVersion':'101','targetApiVersion':'102','staticScope':'true'})
 def test_api_not_bundled(self):
  with zipfile.ZipFile(ROOT/'PixelVcrbt.apk') as z:data=z.read('classes.dex')
  count,offset=struct.unpack_from('<II',data,96)
  strings_count,strings_offset=struct.unpack_from('<II',data,56)
  type_count,type_offset=struct.unpack_from('<II',data,64)
  def string(index):
   at=struct.unpack_from('<I',data,strings_offset+index*4)[0]
   while data[at]&128:at+=1
   at+=1
   return data[at:data.index(0,at)].decode()
  classes=[]
  for n in range(count):
   type_index=struct.unpack_from('<I',data,offset+n*32)[0]
   string_index=struct.unpack_from('<I',data,type_offset+type_index*4)[0]
   classes.append(string(string_index))
  self.assertFalse(any(x.startswith(('Lio/github/libxposed/','Lde/robv/android/xposed/')) for x in classes))
  self.assertIn('Lio/github/icassius/pixelvcrbt/PixelVcrbtBridge;',classes)
 def test_manifest_is_modern(self):
  app=ET.parse(ROOT/'src/main/AndroidManifest.xml').getroot().find('application')
  self.assertEqual(app.get('{http://schemas.android.com/apk/res/android}description'),'@string/xposed_desc')
  self.assertEqual(app.findall('meta-data'),[])
if __name__=='__main__':unittest.main()
