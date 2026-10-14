"""Publish release-only metadata after a successful APK release. No secret output."""
import os,json,base64,hashlib,time,urllib.request,urllib.error,pathlib,xml.etree.ElementTree as ET

def entry(manifest,apk,tag):
    node=ET.parse(manifest).getroot();ns='{http://schemas.android.com/apk/res/android}'
    version=node.get(ns+'versionName');code=int(node.get(ns+'versionCode'))
    if tag!='v'+version:raise ValueError('Tag does not match manifest')
    return dict(versionCode=code,versionName=version,packageName=node.get('package'),tag=tag,
                apk=pathlib.Path(apk).name,sha256=hashlib.sha256(pathlib.Path(apk).read_bytes()).hexdigest())

def merge(feed,candidate):
    if feed.get('schema')!=1:raise ValueError('Unknown update feed schema')
    key='experimental' if '-' in candidate['versionName'] else 'stable'
    previous=feed.get(key)
    if previous and int(previous['versionCode'])>=candidate['versionCode']:return feed
    return dict(feed,**{key:candidate})

def main():
    repo=os.environ['GITHUB_REPOSITORY'];token=os.environ['GH_TOKEN'];tag=os.environ['GITHUB_REF_NAME']
    candidate=entry('src/main/AndroidManifest.xml','dist/PixelVcrbt-'+tag+'.apk',tag)
    url='https://api.github.com/repos/'+repo+'/contents/update.json'
    headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'PixelVcrbt-release'}
    for attempt in range(3):
        try:
            request=urllib.request.Request(url+'?ref=main',headers=headers)
            try:
                with urllib.request.urlopen(request,timeout=30) as response:current=json.load(response)
                feed=json.loads(base64.b64decode(current['content']));sha=current['sha']
            except urllib.error.HTTPError as e:
                if e.code!=404:raise
                feed={'schema':1,'stable':None,'experimental':None};sha=None
            updated=merge(feed,candidate)
            if updated==feed:print('Update feed already current');return
            body={'message':'Update release feed for '+tag,'branch':'main','content':base64.b64encode((json.dumps(updated,ensure_ascii=False,indent=2)+'\n').encode()).decode()}
            if sha:body['sha']=sha
            request=urllib.request.Request(url,data=json.dumps(body).encode(),headers=headers,method='PUT')
            with urllib.request.urlopen(request,timeout=30) as response:json.load(response)
            print('Release update feed published');return
        except urllib.error.HTTPError as e:
            if e.code not in (409,422) or attempt==2:raise RuntimeError('Update feed publication failed, HTTP '+str(e.code)) from None
            time.sleep(1)

if __name__=='__main__':main()
