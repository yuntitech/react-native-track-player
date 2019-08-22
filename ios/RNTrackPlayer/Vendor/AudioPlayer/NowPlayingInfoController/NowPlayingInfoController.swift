//
//  MediaInfoController.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 15/03/2018.
//

import Foundation
import MediaPlayer


public class NowPlayingInfoController: NowPlayingInfoControllerProtocol {
    
    private var _infoCenter: NowPlayingInfoCenter
    private var _info: [String: Any] = [:]
    
    var infoCenter: NowPlayingInfoCenter {
        return _infoCenter
    }
    
    var info: [String: Any] {
        return _info
    }
    
    public required init() {
        self._infoCenter = MPNowPlayingInfoCenter.default()
    }
    
    public required init(infoCenter: NowPlayingInfoCenter) {
        self._infoCenter = infoCenter
    }
    
    public func set(keyValues: [NowPlayingInfoKeyValue]) {
        DispatchQueue.main.async { [weak self] in
            guard let `self` = self else { return }
            keyValues.forEach { (keyValue) in
                self._info[keyValue.getKey()] = keyValue.getValue()
            }
            self._infoCenter.nowPlayingInfo = self._info
        }
       
    }
    
    public func set(keyValue: NowPlayingInfoKeyValue) {
        /**
         1. 这个方法可能会有多个线程同时访问, 此时会可能出问题, 多线程同时访问_infoCenter.nowPlayingInfo 可能会导致EXC_BAD_ACCESS
         2. 假如 AB 线程都要设置nowPlayingInfo
         3. A线程先通过 getter 拿到nowPlayingInfo属性(release -> retain), 然后切换到 B 线程, refCount == 1
         4. B线程在进行对 nowPlayingInfo 进行 setter 的时候setter 方法里会先进行 release, 还没来得及 retain 的时候此时切换为 A线程,(refCount == 0), A 线程这时候访问这个属性就坏内存访问了
         */
        DispatchQueue.main.async {[weak self] in
            guard let `self` = self else { return }
            self._info[keyValue.getKey()] = keyValue.getValue()
            self._infoCenter.nowPlayingInfo = self._info
        }
    }
    
    public func clear() {
        DispatchQueue.main.async {[weak self] in
            guard let `self` = self else { return }
            self._info = [:]
            self._infoCenter.nowPlayingInfo = self._info
        }
    }
    
}
