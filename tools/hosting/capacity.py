"""Real persistent-socket capacity experiment against a separate synthetic hosted process."""
import argparse
import asyncio
import hashlib
import json
import math
from pathlib import Path
import statistics
import time


def identity(index):return 'rs-'+hashlib.sha256(('synthetic-'+str(index)).encode()).hexdigest()
def token(index):return 'synthetic-capacity-token-'+str(index).zfill(8)+'x'*32


def configuration(port):
    return {'port':port,'devices':[{'userId':'synthetic-user-'+str(i),'installationId':'synthetic-install-'+str(i),
            'tokenSha256':hashlib.sha256(token(i).encode()).hexdigest(),'accounts':[identity(i)]} for i in range(1000)]}


async def experiment(port,seconds):
    reads=[];decisions=[];errors=[];connections=[];maximum=0;ready=asyncio.Event()
    async def request(pair,index,route,body=None):
        reader,writer=pair
        payload=b'' if body is None else json.dumps(body,separators=(',',':')).encode()
        message=(('GET' if body is None else 'POST')+' /service/v1/'+route+' HTTP/1.1\r\nHost: localhost\r\n'
            +'X-Flipping-Friend-Pilot-Token: '+token(index)+'\r\nContent-Type: application/json\r\nContent-Length: '+str(len(payload))
            +'\r\nConnection: keep-alive\r\n\r\n').encode()+payload
        started=time.perf_counter();writer.write(message);await writer.drain()
        header=await asyncio.wait_for(reader.readuntil(b'\r\n\r\n'),5)
        lines=header.decode().split('\r\n');status=int(lines[0].split()[1]);size=int(next(line.split(':',1)[1].strip() for line in lines if line.lower().startswith('content-length:')))
        content=await asyncio.wait_for(reader.readexactly(size),5)
        reads.append((time.perf_counter()-started)*1000)
        return status,json.loads(content)
    semaphore=asyncio.Semaphore(40)
    async def connect(index):
        async with semaphore:
            pair=await asyncio.open_connection('127.0.0.1',port)
            status,_=await request(pair,index,'health')
            if status!=200:raise RuntimeError('health '+str(status))
            connections.append((index,pair))
    await asyncio.gather(*(connect(i) for i in range(1000)))
    maximum=len(connections);started=time.monotonic()
    async def client(index,pair):
        frame=0
        while time.monotonic()-started<seconds:
            try:
                if index<100:
                    frame+=1;now=int(time.time());context=hashlib.sha256((identity(index)+str(frame)).encode()).hexdigest()
                    candidates=[]
                    for offset in range(8):
                        item=10000+(index+offset)%100
                        candidates.append({'itemId':item,'itemName':'Synthetic','group':'','targetBuyPrice':100,'targetSellPrice':120,
                          'equilibriumBuyPrice':100,'exitBuyPrice':100,'equilibriumSellPrice':120,'exitSellPrice':120,
                          'quantity':30,'buyLimitRemaining':10000,'capitalRequired':3000,'netProfit':540,'worstLoss':150,'unwindLoss':1,'horizonHours':1,
                          'buyFillProbability':.95,'sellFillProbability':.95,'buyHours':.1,'sellHours':.1,'expiresAt':now+90})
                    body={'protocol':'hosted-pilot-v1','accountId':identity(index),'installationId':'synthetic-install-'+str(index),
                          'generation':1,'frameOrder':frame,'context':context,'observedAt':now,'marketAt':now,'preferredProfit':50000,
                          'coordinate':True,'candidates':candidates,
                          'demand':[{'itemId':10000+(index+offset)%100,'buying':10,'selling':20,'uncertain':False} for offset in range(8)]}
                    began=time.perf_counter()
                    for attempt in range(6):
                        status,result=await request(pair,index,'allocations',body)
                        if status==200:
                            if result.get('itemId',0)<=0:raise RuntimeError('no synthetic allocation')
                            decisions.append((time.perf_counter()-began)*1000);break
                        if status!=202:raise RuntimeError('allocation '+str(status))
                        await asyncio.sleep(.1)
                    else:raise RuntimeError('allocation deadline')
                    await asyncio.sleep(max(.1,5-(time.perf_counter()-began)))
                else:
                    status,_=await request(pair,index,'health')
                    if status!=200:raise RuntimeError('health '+str(status))
                    await asyncio.sleep(15)
            except Exception as error:errors.append(type(error).__name__+': '+str(error));return
    await asyncio.gather(*(client(index,pair) for index,pair in connections))
    for _,(_,writer) in connections:writer.close()
    def stats(values):
        values=sorted(values)
        return {'count':len(values),'p50ms':round(statistics.median(values),2) if values else None,
            'p95ms':round(values[max(0,math.ceil(len(values)*.95)-1)],2) if values else None,
            'p99ms':round(values[max(0,math.ceil(len(values)*.99)-1)],2) if values else None}
    return {'connectedClients':maximum,'activelyPlanning':100,'durationSeconds':round(time.monotonic()-started,2),
            'frameIntervalSeconds':5,'http':stats(reads),'decisions':stats(decisions),'errors':errors[:20],'errorCount':len(errors),
            'scope':'synthetic hosted allocation and retained HTTP reads; excludes local financial ACK/client rendering'}


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=38889);parser.add_argument('--seconds',type=int,default=60)
    parser.add_argument('--config',type=Path);parser.add_argument('--output',type=Path);args=parser.parse_args()
    if args.config:args.config.write_text(json.dumps(configuration(args.port)));raise SystemExit(0)
    result=asyncio.run(experiment(args.port,args.seconds));print(json.dumps(result))
    if args.output:args.output.write_text(json.dumps(result,indent=2))
    if result['errorCount'] or result['decisions']['count']<100:raise SystemExit(1)
