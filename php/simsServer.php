<?php

/*
 * simsServer.php
 * self-organized synchronization server for dynamic adaptive streaming over http
 *
 * Copyright (c) 2014, Stefan Petscharnig. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */
	/*
		storing session info and altered mpds and sends them back to clients
	*/
	function calc_validity($duration){
	
		$duration = str_ireplace('pt','',$duration);
		$duration = str_ireplace('h',';',$duration);
		$duration = str_ireplace('m',';',$duration);
		$duration = str_ireplace('s',';',$duration);
		$duration = preg_split('/;/', $duration);
		$duration = ( $duration[2] + 60* ($duration[1]  + 60*$duration[0]));
		
		$date = new DateTime();
		
		return round($duration*1.5) + $date->getTimestamp(); // time_millis();
	}
	if(isset($_GET['mediaSource'])){
		$mediaSource = $_GET['mediaSource'];
	}else{
		$mediaSource = 'http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=7181C59D0252B285D593E1B61D985D5B7C98DE2A.5B445837F55A40E0F28AACAA047982E372D177E2&key=ik0';
	}if(isset($_GET['ip'])){
		$ip = $_GET['ip'];
	}else{
		$ip = getenv('REMOTE_ADDR');
	}
	if(isset($_GET['port'])){
		$port = $_GET['port'];
	}else{
		$port = getenv('REMOTE_PORT');
	}
	if(isset($_GET['session_key'])){
		$session_key = $_GET['session_key'];
	}else{
		$session_key = 1;
	}
	if(isset($_GET['ip'])){
		$ip = $_GET['ip'];
	}else{
		$ip = getenv('REMOTE_ADDR');
	}
	
	$file_path = "$session_key.xml";
	
	$first_from_session = !file_exists($file_path);
	$invalid = false;
	
	
	if(!$first_from_session){
		//open file and check validity
		$fp = file_get_contents($file_path);
		$mpd = new SimpleXMLElement($fp);
		//is the xml on the server invalid?
		$mpd->registerXPathNamespace('p','urn:mpeg:DASH:schema:MPD:2011');
		
		$peers = $mpd->xpath('//p:session')[0];
		$atts = $peers[0]->attributes();
		
		$now = new DateTime("now");
		$validThru = new DateTime();
		$l = (int) $atts['validThru'];
		$validThru->setTimestamp( $l );
		
		$invalid = $now > $validThru;
		
	}
	if($invalid || $first_from_session){
	
		//fetch mpd for bunny from server
		$ch = curl_init($mediaSource);
		$fp = fopen($file_path, "w");
		curl_setopt($ch, CURLOPT_FILE, $fp);
		curl_setopt($ch, CURLOPT_HEADER, 0);

		curl_exec($ch);
		curl_close($ch);
		
		fclose($fp);
	
	
		$fp = file_get_contents($file_path);
		$mpd = new SimpleXMLElement($fp);
		
		$root_attr = $mpd->attributes();
		$end_time = calc_validity($root_attr['mediaPresentationDuration']);
		$peers = $mpd->addChild('session');
		$peers->addAttribute("validThru", $end_time);
		$peers->addAttribute("session_id", $session_key);
	}
	
	$ns = 'urn:mpeg:DASH:schema:MPD:2011';
	$q = "//p:peer[@ip=\"$ip\"]";	
	$mpd->registerXPathNamespace('p',$ns);		
	$here = $mpd->xpath($q);
	
	if(count($here) == 0){
		//add current peer to peers
		
		$newPeer = $peers->addChild('peer');
		
		$mpd->registerXPathNamespace('p',$ns);		
		$peerCount = count($mpd->xpath('//p:peer'));
		
		$newPeer->addAttribute("id", $peerCount);		
		$newPeer->addAttribute("ip", $ip);		
		$newPeer->addAttribute("port", $port);
		
	}else{
		//duplicate ip
	}
	
	$mpd->asXML($file_path);
		
	header('Content-type: text/xml');
	readfile($file_path);
?>