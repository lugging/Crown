/*
 * Copyright (c) 2018-2022 Caratacus, (caratacus@qq.com).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.crown.controller;

import java.util.List;
import java.util.Objects;

import org.apache.commons.codec.digest.Md5Crypt;
import org.crown.common.annotations.Resources;
import org.crown.common.api.ApiAssert;
import org.crown.common.api.model.responses.ApiResponses;
import org.crown.common.emuns.ErrorCodeEnum;
import org.crown.common.framework.controller.SuperController;
import org.crown.common.kit.TypeUtils;
import org.crown.emuns.StatusEnum;
import org.crown.model.dto.UserDTO;
import org.crown.model.dto.UserDetailsDTO;
import org.crown.model.entity.User;
import org.crown.model.entity.UserRole;
import org.crown.model.parm.UserInfoPARM;
import org.crown.model.parm.UserPARM;
import org.crown.service.IUserRoleService;
import org.crown.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;

/**
 * <p>
 * 系统用户表 前端控制器
 * </p>
 *
 * @author Caratacus
 * @since 2018-10-25
 */
@Api(tags = {"User"}, description = "用户操作相关接口")
@RestController
@RequestMapping(value = "/user", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@Validated
public class UserRestController extends SuperController {

    @Autowired
    private IUserService userService;
    @Autowired
    private IUserRoleService userRoleService;

    @Resources(verify = false)
    @ApiOperation("查询所有用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "loginName", value = "需要检查的账号", paramType = "query"),
            @ApiImplicitParam(name = "nickname", value = "需要检查的账号", paramType = "query"),
            @ApiImplicitParam(name = "status", value = "需要检查的账号", paramType = "query")
    })
    @GetMapping
    public ApiResponses<IPage<UserDTO>> page(@RequestParam(value = "loginName", required = false) String loginName,
                                             @RequestParam(value = "nickname", required = false) String nickname,
                                             @RequestParam(value = "status", required = false) StatusEnum status) {
        IPage<User> page = userService.page(this.<User>getPage(), Wrappers.<User>lambdaQuery().likeRight(StringUtils.isNotEmpty(loginName), User::getLoginName, loginName).likeRight(StringUtils.isNotEmpty(nickname), User::getNickname, nickname).eq(Objects.nonNull(status), User::getStatus, status));
        return success(page.convert(e -> e.convert(UserDTO.class)));
    }

    @Resources(verify = false)
    @ApiOperation("查询单个用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "用户ID", required = true, paramType = "path")
    })
    @GetMapping("/{id}")
    public ApiResponses<UserDTO> get(@PathVariable("id") Integer id) {
        User user = userService.getById(id);
        ApiAssert.notNull(ErrorCodeEnum.USER_NOT_FOUND, user);
        UserDTO userDTO = user.convert(UserDTO.class);
        List<Integer> roleIds = userRoleService.listObjs(Wrappers.<UserRole>lambdaQuery().select(UserRole::getRoleId).eq(UserRole::getId, id), TypeUtils::castToInt);
        userDTO.setRoleIds(roleIds);
        return success(userDTO);
    }

    @Resources(verify = false)
    @ApiOperation("重置用户密码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "用户ID", required = true, paramType = "path")
    })
    @PutMapping("/{id}/password/reset")
    public ApiResponses<Void> resetPwd(@PathVariable("id") Integer id) {
        userService.resetPwd(id);
        return empty();
    }

    @Resources
    @ApiOperation("设置用户状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "用户ID", required = true, paramType = "path")
    })
    @PutMapping("/{id}/status")
    public ApiResponses<Void> updateStatus(@PathVariable("id") Integer id, @RequestBody @Validated(UserPARM.Status.class) UserPARM userPARM) {
        userService.updateStatus(id, userPARM.getStatus());
        return empty();
    }

    @Resources(verify = false)
    @ApiOperation("创建用户")
    @PostMapping
    public ApiResponses<Void> create(@RequestBody @Validated(UserPARM.Create.class) UserPARM userPARM) {
        int count = userService.count(Wrappers.<User>lambdaQuery().eq(User::getLoginName, userPARM.getLoginName()));
        ApiAssert.isTrue(ErrorCodeEnum.USERNAME_ALREADY_EXISTS, count == 0);
        User user = userPARM.convert(User.class);
        //没设置密码 设置默认密码
        if (StringUtils.isEmpty(user.getPassword())) {
            user.setPassword(Md5Crypt.apr1Crypt(user.getLoginName(), user.getLoginName()));
        }
        //默认禁用
        user.setStatus(StatusEnum.DISABLE);
        userService.save(user);
        userService.saveUserRoles(user.getId(), userPARM.getRoleIds());
        return empty();
    }

    @Resources
    @ApiOperation("修改用户")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "用户ID", required = true, paramType = "path")
    })
    @PutMapping("/{id}")
    public ApiResponses<Void> update(@PathVariable("id") Integer id, @RequestBody @Validated(UserPARM.Update.class) UserPARM userPARM) {
        User user = userPARM.convert(User.class);
        user.setId(id);
        userService.updateById(user);
        userService.saveUserRoles(id, userPARM.getRoleIds());
        return empty();
    }

    @Resources(verify = false)
    @ApiOperation("获取用户详情")
    @GetMapping("/details")
    public ApiResponses<UserDetailsDTO> getUserDetails() {
        Integer uid = currentUid();
        UserDetailsDTO userDetails = userService.getUserDetails(uid);
        return success(userDetails);
    }

    @Resources
    @ApiOperation("修改用户信息")
    @PutMapping("/info")
    public ApiResponses<Void> updateUserInfo(@RequestBody @Validated UserInfoPARM userInfoPARM) {
        Integer uid = currentUid();
        User user = userInfoPARM.convert(User.class);
        user.setId(uid);
        userService.updateById(user);
        return empty();
    }

}

