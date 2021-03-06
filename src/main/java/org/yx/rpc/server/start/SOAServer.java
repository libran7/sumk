/**
 * Copyright (C) 2016 - 2017 youtongluan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yx.rpc.server.start;

import java.io.IOException;
import java.util.Set;
import java.util.StringJoiner;

import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.yx.bean.IOC;
import org.yx.bean.Plugin;
import org.yx.conf.AppInfo;
import org.yx.conf.Profile;
import org.yx.log.Log;
import org.yx.rpc.ActionHolder;
import org.yx.rpc.ZKConst;
import org.yx.rpc.ZkClientHolder;
import org.yx.rpc.server.ReqHandlerFactorysBean;
import org.yx.rpc.server.ServerListener;
import org.yx.util.StringUtils;

/**
 * 启动服务器端
 * 
 * @author 游夏
 *
 */
public class SOAServer implements Plugin {

	private boolean started = false;
	private final int port;
	private ServerListener server;
	private String zkUrl;
	private String path;

	public SOAServer(int port) {
		super();
		this.port = port;
	}

	public synchronized void start() {
		if (started) {
			return;
		}
		try {
			String ip = AppInfo.get("soa.host");

			ip = AppInfo.get("soa.zk.host", ip);
			if (StringUtils.isEmpty(ip) || "0.0.0.0".equals(ip)) {
				ip = AppInfo.getIp();
			}
			path = ZkClientHolder.SOA_ROOT + "/" + ip + ":" + port;
			zkUrl = AppInfo.getZKUrl();
			ZkClient client = ZkClientHolder.getZkClient(zkUrl);
			ZkClientHolder.makeSure(client, ZkClientHolder.SOA_ROOT);

			startServer(ip, port);

			client.delete(path);
			IZkStateListener stateListener = new IZkStateListener() {

				@Override
				public void handleStateChanged(KeeperState state) throws Exception {
					Log.get("sumk.rpc").info("zk state changed:{}", state);
				}

				@Override
				public void handleNewSession() throws Exception {
					client.createEphemeral(path, createZkRouteData());
				}

				@Override
				public void handleSessionEstablishmentError(Throwable error) throws Exception {
					Log.get("sumk.rpc").error("SessionEstablishmentError#" + error.getMessage(), error);
				}

			};
			client.createEphemeral(path, createZkRouteData());
			client.subscribeStateChanges(stateListener);
			started = true;
		} catch (Exception e) {
			Log.printStack(e);
			System.exit(-1);
		}
	}

	private void startServer(String ip, int port) throws Exception {

		server = new ServerListener(ip, port, IOC.get(ReqHandlerFactorysBean.class).create());
		server.run();
	}

	private String createZkRouteData() {
		Set<String> methods = ActionHolder.soaSet();
		StringJoiner sj = new StringJoiner("\n");
		if (methods != null && methods.size() > 0) {
			sj.add(ZKConst.METHODS + "=" + String.join(",", methods.toArray(new String[methods.size()])));
		}
		sj.add(ZKConst.FEATURE + "=" + Profile.featureInHex());
		sj.add(ZKConst.START + "=" + System.currentTimeMillis());
		return sj.toString();
	}

	@Override
	public synchronized void stop() {
		ZkClient client = ZkClientHolder.getZkClient(zkUrl);
		if (client != null) {
			client.unsubscribeAll();
			client.delete(path);
		}

		if (this.server != null) {
			try {
				this.server.stop();
			} catch (IOException e) {
				Log.printStack("rpc", e);
			}
		}
	}
}
