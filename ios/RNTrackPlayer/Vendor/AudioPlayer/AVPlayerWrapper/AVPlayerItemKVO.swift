//
//  AVPlayerItemKVO.swift
//  RNTrackPlayer
//
//  Created by yeteam on 2019/9/29.
//  Copyright © 2019 David Chavez. All rights reserved.
//

import AVKit

class AVPlayerItemKVO: AVPlayerItem {
  var deinitClosure: (() -> Void)?

  deinit {
    deinitClosure?()
  }
}
