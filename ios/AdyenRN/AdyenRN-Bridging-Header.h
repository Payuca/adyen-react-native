//
//  AdyenRN-Bridging-Header.h
//  AdyenRN
//
//  Created by Dmitry Belov on 21/02/2019.
//  Copyright © 2019 Dmitry Belov. All rights reserved.
//

#if __has_include(<React/RCTBridgeModule.h>) // React Native >= 0.40
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#else // React Native < 0.40
#import "RCTBridgeModule.h"
#import <RCTEventEmitter.h>
#endif
