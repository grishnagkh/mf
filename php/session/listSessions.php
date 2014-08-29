<?php

/*
 * listSessions.php
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

function valid($file){

	$fp = file_get_contents($file);

	$xml = new SimpleXMLElement($fp);
 	$xml->registerXPathNamespace('p','urn:mpeg:DASH:schema:MPD:2011');
	$xpathrc = $xml->xpath('//p:session');
	$peers = $xpathrc[0];
	$atts = $peers[0]->attributes();


	$now = new DateTime("now");
	$validThru = new DateTime();
	$l = (int) $atts['validThru'];
	$validThru->setTimestamp( $l );

	//echo "\nNOW".$now->getTimestamp()."/VAT:".$validThru->getTimeStamp()."\n";


	if($validThru<$now){
		return false;
	}

	return true;
}

if ($handle = opendir('.')) {  

    while (false !== ($file = readdir($handle))) {
	if (strtolower(substr($file, strrpos($file, '.') + 1)) == 'xml'){

		if(valid($file)){
			echo substr($file, 0, strrpos($file, '.') ).";";
		}else{
			//clear it
			unlink($file);
		}
	}
    }
  
    closedir($handle);
}
?>